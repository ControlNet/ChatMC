package space.controlnet.mineagent.common.client.automation;

import dev.architectury.event.events.client.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import space.controlnet.mineagent.common.client.screen.AiTerminalScreen;
import space.controlnet.mineagent.common.client.screen.AiTerminalStatusScreen;
import space.controlnet.mineagent.common.menu.AiTerminalMenu;
import space.controlnet.mineagent.core.client.ClientAiSettings;
import space.controlnet.mineagent.core.client.ClientSessionIndex;
import space.controlnet.mineagent.core.client.ClientSessionStore;
import space.controlnet.mineagent.core.client.ClientToolCatalog;
import space.controlnet.mineagent.core.tools.ToolCatalogEntry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AiTerminalUiAutomation {
    private static final String SCENARIO_ENV = "MINEAGENT_UI_CAPTURE_SCENARIO";
    private static final String OUTPUT_DIR_ENV = "MINEAGENT_UI_CAPTURE_OUTPUT_DIR";
    private static final String OUTPUT_NAME_ENV = "MINEAGENT_UI_CAPTURE_BASENAME";
    private static final String SETTLE_TICKS_ENV = "MINEAGENT_UI_CAPTURE_SETTLE_TICKS";

    private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

    private static CaptureRequest request;
    private static AiTerminalUiPreviewState previewState;
    private static boolean previewApplied;
    private static boolean terminalOpenRequested;
    private static boolean statusScreenTriggered;
    private static boolean screenshotTriggered;
    private static int ticksSinceOpen;
    private static int terminalOpenWaitTicks;
    private static int toolCatalogWaitTicks;
    private static PendingCapture pendingCapture;
    private static RuntimeException failure;

    private AiTerminalUiAutomation() {
    }

    public static void init() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }
        request = CaptureRequest.fromEnvironment().orElse(null);
        if (request == null) {
            return;
        }
        ClientTickEvent.CLIENT_POST.register(AiTerminalUiAutomation::onClientTick);
    }

    private static void onClientTick(Minecraft minecraft) {
        if (failure != null) {
            throw failure;
        }
        if (request == null || minecraft == null) {
            return;
        }
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (!(minecraft.screen instanceof AiTerminalScreen) && !(minecraft.screen instanceof AiTerminalStatusScreen)) {
            if (usesLiveStatusFlow()) {
                ensureLiveTerminalOpen(minecraft);
            } else {
                openPreviewScreen(minecraft);
            }
            return;
        }

        if (!previewApplied && minecraft.screen instanceof AiTerminalScreen terminalScreen) {
            if (usesLiveStatusFlow()) {
                previewApplied = true;
                ticksSinceOpen = 0;
            } else {
                applyPreviewState(terminalScreen);
            }
            return;
        }

        if (minecraft.screen instanceof AiTerminalScreen terminalScreen
                && shouldOpenStatusScreen()
                && !statusScreenTriggered) {
            if (!isStatusCatalogReady()) {
                toolCatalogWaitTicks++;
                if (toolCatalogWaitTicks > 200) {
                    fail(new IllegalStateException("mineagent-ui-capture/tool-catalog-timeout"));
                }
                return;
            }
            terminalScreen.triggerStatusButtonForAutomation();
            statusScreenTriggered = true;
            return;
        }

        minecraft.getToasts().clear();

        if (!screenshotTriggered) {
            ticksSinceOpen++;
            if (ticksSinceOpen < request.settleTicks()) {
                return;
            }
            try {
                AiTerminalUiSnapshot snapshot = captureSnapshot(minecraft);
                AiTerminalUiAssertions.assertMatches(request.scenarioId(), snapshot);
                pendingCapture = PendingCapture.begin(minecraft, request.outputPath());
                screenshotTriggered = true;
            } catch (RuntimeException exception) {
                fail(exception);
            }
            return;
        }

        if (pendingCapture == null) {
            return;
        }

        try {
            Optional<Path> completedPath = pendingCapture.tryComplete();
            if (completedPath.isPresent()) {
                System.out.println("[mineagent-ui-capture] saved " + completedPath.get());
                minecraft.stop();
                scheduleForceHalt();
                return;
            }
            if (pendingCapture.hasTimedOut()) {
                fail(new IllegalStateException("mineagent-ui-capture/timeout -> screenshot file was not produced"));
            }
        } catch (IOException exception) {
            fail(new RuntimeException("mineagent-ui-capture/io", exception));
        }
    }

    private static void openPreviewScreen(Minecraft minecraft) {
        if (previewState == null) {
            previewState = AiTerminalUiFixtures.create(
                    request.scenarioId(),
                    minecraft.player.getUUID(),
                    minecraft.player.getName().getString()
            );
            ClientAiSettings.setAiLocaleOverride(previewState.aiLocaleOverride());
            ClientSessionStore.set(previewState.snapshot());
            ClientSessionIndex.set(previewState.sessionSummaries());
        }
        AiTerminalMenu menu = new AiTerminalMenu(0, minecraft.player.getInventory(), null, minecraft.player.blockPosition(), null);
        AiTerminalScreen terminalScreen = new AiTerminalScreen(menu, minecraft.player.getInventory(), Component.translatable("ui.mineagent.terminal.title"));
        minecraft.setScreen(terminalScreen);
        ticksSinceOpen = 0;
        previewApplied = false;
        terminalOpenRequested = false;
        terminalOpenWaitTicks = 0;
        toolCatalogWaitTicks = 0;
        statusScreenTriggered = false;
        screenshotTriggered = false;
        pendingCapture = null;
    }

    private static void ensureLiveTerminalOpen(Minecraft minecraft) {
        if (minecraft.getConnection() == null) {
            return;
        }
        if (!terminalOpenRequested) {
            minecraft.getConnection().sendCommand("mineagent open");
            terminalOpenRequested = true;
            terminalOpenWaitTicks = 0;
            ticksSinceOpen = 0;
            previewApplied = false;
            statusScreenTriggered = false;
            screenshotTriggered = false;
            toolCatalogWaitTicks = 0;
            pendingCapture = null;
            return;
        }
        terminalOpenWaitTicks++;
        if (terminalOpenWaitTicks > 200) {
            fail(new IllegalStateException("mineagent-ui-capture/open-terminal-timeout"));
        }
    }

    private static void applyPreviewState(AiTerminalScreen terminalScreen) {
        if (previewState == null) {
            return;
        }
        terminalScreen.applyAutomationPreview(previewState);
        previewApplied = true;
    }

    private static AiTerminalUiSnapshot captureSnapshot(Minecraft minecraft) {
        if (minecraft.screen instanceof AiTerminalStatusScreen statusScreen) {
            return statusScreen.captureAutomationSnapshot();
        }
        if (minecraft.screen instanceof AiTerminalScreen terminalScreen) {
            return terminalScreen.captureAutomationSnapshot();
        }
        throw new IllegalStateException("mineagent-ui-capture/screen -> unsupported screen "
                + (minecraft.screen == null ? "null" : minecraft.screen.getClass().getName()));
    }

    private static boolean usesLiveStatusFlow() {
        if (request == null) {
            return false;
        }
        return request.scenarioId() == AiTerminalUiScenarioId.STATUS_BUTTON
                || request.scenarioId() == AiTerminalUiScenarioId.STATUS_PANEL;
    }

    private static boolean shouldOpenStatusScreen() {
        if (request != null && request.scenarioId() == AiTerminalUiScenarioId.STATUS_PANEL) {
            return true;
        }
        return previewState != null && previewState.statusScreenOpen();
    }

    private static boolean isStatusCatalogReady() {
        if (request == null || request.scenarioId() != AiTerminalUiScenarioId.STATUS_PANEL) {
            return true;
        }
        boolean hasBuiltIn = false;
        boolean hasExtension = false;
        boolean hasMcp = false;
        for (ToolCatalogEntry tool : ClientToolCatalog.get()) {
            if (tool == null || tool.toolName() == null || tool.toolName().isBlank()) {
                continue;
            }
            String providerId = tool.providerId();
            if (providerId != null && providerId.startsWith("mcp.runtime.")) {
                hasMcp = true;
                continue;
            }
            if ("mc".equals(providerId) || "http".equals(providerId)) {
                hasBuiltIn = true;
                continue;
            }
            hasExtension = true;
        }
        return hasBuiltIn && hasExtension && hasMcp;
    }

    private static void scheduleForceHalt() {
        Thread haltThread = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException ignored) {
            }
            Runtime.getRuntime().halt(0);
        }, "mineagent-ui-capture-halt");
        haltThread.setDaemon(true);
        haltThread.start();
    }

    private static void fail(RuntimeException exception) {
        failure = exception;
        throw exception;
    }

    private static final class CaptureRequest {
        private final AiTerminalUiScenarioId scenarioId;
        private final Path outputPath;
        private final int settleTicks;

        private CaptureRequest(AiTerminalUiScenarioId scenarioId, Path outputPath, int settleTicks) {
            this.scenarioId = scenarioId;
            this.outputPath = outputPath;
            this.settleTicks = settleTicks;
        }

        private static Optional<CaptureRequest> fromEnvironment() {
            String rawScenario = System.getenv(SCENARIO_ENV);
            if (rawScenario == null || rawScenario.isBlank()) {
                return Optional.empty();
            }
            AiTerminalUiScenarioId scenarioId = AiTerminalUiScenarioId.parse(rawScenario);
            String outputDir = System.getenv(OUTPUT_DIR_ENV);
            Path baseOutputDir = outputDir == null || outputDir.isBlank()
                    ? Path.of("ui-captures")
                    : Path.of(outputDir);
            String baseName = System.getenv(OUTPUT_NAME_ENV);
            if (baseName == null || baseName.isBlank()) {
                baseName = scenarioId.externalName();
            }
            int settleTicks = 20;
            String rawSettleTicks = System.getenv(SETTLE_TICKS_ENV);
            if (rawSettleTicks != null && !rawSettleTicks.isBlank()) {
                settleTicks = Integer.parseInt(rawSettleTicks.trim());
            }
            return Optional.of(new CaptureRequest(scenarioId, baseOutputDir.resolve(baseName + ".png").toAbsolutePath(), Math.max(1, settleTicks)));
        }

        private AiTerminalUiScenarioId scenarioId() {
            return scenarioId;
        }

        private Path outputPath() {
            return outputPath;
        }

        private int settleTicks() {
            return settleTicks;
        }
    }

    private static final class PendingCapture {
        private final Path screenshotDir;
        private final Path outputPath;
        private final Path previousLatest;
        private final long startedAtMillis;

        private PendingCapture(Path screenshotDir, Path outputPath, Path previousLatest, long startedAtMillis) {
            this.screenshotDir = screenshotDir;
            this.outputPath = outputPath;
            this.previousLatest = previousLatest;
            this.startedAtMillis = startedAtMillis;
        }

        private static PendingCapture begin(Minecraft minecraft, Path outputPath) {
            Path screenshotDir = minecraft.gameDirectory.toPath().resolve("screenshots");
            try {
                Files.createDirectories(screenshotDir);
                Files.createDirectories(outputPath.getParent());
            } catch (IOException exception) {
                throw new RuntimeException("mineagent-ui-capture/create-dirs", exception);
            }
            Path previousLatest = newestPng(screenshotDir).orElse(null);
            Screenshot.grab(minecraft.gameDirectory, minecraft.getMainRenderTarget(), component -> {
            });
            return new PendingCapture(screenshotDir, outputPath, previousLatest, System.currentTimeMillis());
        }

        private Optional<Path> tryComplete() throws IOException {
            Optional<Path> latest = newestPng(this.screenshotDir);
            if (latest.isEmpty()) {
                return Optional.empty();
            }
            Path candidate = latest.get();
            if (Objects.equals(candidate, this.previousLatest)) {
                return Optional.empty();
            }
            if (Files.getLastModifiedTime(candidate).toMillis() < this.startedAtMillis) {
                return Optional.empty();
            }
            Files.copy(candidate, this.outputPath, StandardCopyOption.REPLACE_EXISTING);
            return Optional.of(this.outputPath);
        }

        private boolean hasTimedOut() {
            return System.currentTimeMillis() - this.startedAtMillis > 30_000L;
        }

        private static Optional<Path> newestPng(Path directory) {
            if (!Files.isDirectory(directory)) {
                return Optional.empty();
            }
            try (var stream = Files.list(directory)) {
                return stream
                        .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".png"))
                        .max(Comparator.comparingLong(PendingCapture::lastModifiedOrZero));
            } catch (IOException exception) {
                return Optional.empty();
            }
        }

        private static long lastModifiedOrZero(Path path) {
            try {
                return Files.getLastModifiedTime(path).toMillis();
            } catch (IOException exception) {
                return 0L;
            }
        }
    }
}

package space.controlnet.mineagent.common.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import space.controlnet.mineagent.common.client.automation.AiTerminalUiSnapshot;
import space.controlnet.mineagent.common.client.screen.components.FlatButton;
import space.controlnet.mineagent.common.client.screen.components.UiButtonStyle;
import space.controlnet.mineagent.core.client.ClientToolCatalog;
import space.controlnet.mineagent.core.tools.ToolCatalogEntry;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_ACCENT_CYAN;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_BG_DARK;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_BG_PANEL;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_BG_PANEL_TRANSPARENT;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_BORDER;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_PRIMARY_FLUIX_DIM;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_TEXT_DIM;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.COLOR_TEXT_MAIN;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.FONT_SCALE;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.HEADER_HEIGHT;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.PADDING;
import static space.controlnet.mineagent.common.client.screen.AiTerminalConstants.TITLE_FONT_SCALE;

public final class AiTerminalStatusScreen extends Screen {
    private static final int SCREEN_WIDTH = 420;
    private static final int SCREEN_HEIGHT = 250;
    private static final int BACK_BUTTON_WIDTH = 36;
    private static final int BACK_BUTTON_HEIGHT = 18;
    private static final int PANEL_HEADER_HEIGHT = 16;
    private static final int CONTENT_GAP = 8;
    private static final int LINE_SPACING = 2;

    private final Screen parent;

    private Button backButton;
    private int left;
    private int top;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private int maxScrollLines;
    private int scrollOffsetLines;
    private List<ToolSection> sections = List.of();
    private List<StatusLine> lines = List.of();

    public AiTerminalStatusScreen(Screen parent) {
        super(Component.literal("Status"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        computeLayout();
        refreshSections();
        this.backButton = new FlatButton(
                this.left + SCREEN_WIDTH - PADDING - BACK_BUTTON_WIDTH,
                this.top + (HEADER_HEIGHT - BACK_BUTTON_HEIGHT) / 2,
                BACK_BUTTON_WIDTH,
                BACK_BUTTON_HEIGHT,
                Component.literal("Back"),
                button -> onClose(),
                UiButtonStyle.GHOST
        );
        this.addRenderableWidget(this.backButton);
    }

    public AiTerminalUiSnapshot captureAutomationSnapshot() {
        ToolSection builtIn = firstSection(ToolSectionKind.BUILT_IN);
        ToolSection mcp = firstSection(ToolSectionKind.MCP);
        List<ToolCatalogEntry> extensionTools = toolsForKind(ToolSectionKind.EXTENSION);
        return new AiTerminalUiSnapshot(
                true,
                getClass().getSimpleName(),
                "IDLE",
                false,
                false,
                false,
                false,
                false,
                0,
                0,
                false,
                0,
                -1,
                "",
                0,
                0,
                false,
                this.sections.size(),
                sectionLabels(),
                builtIn.tools().size(),
                extensionTools.size(),
                mcp.tools().size(),
                toolNames(builtIn.tools()),
                toolNames(extensionTools),
                toolNames(mcp.tools())
        );
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        refreshSections();
        this.renderBackground(guiGraphics);
        renderFrame(guiGraphics);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderText(guiGraphics);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.maxScrollLines <= 0 || !isWithinContent(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        int direction = delta > 0 ? -1 : 1;
        this.scrollOffsetLines = clamp(this.scrollOffsetLines + direction, 0, this.maxScrollLines);
        return true;
    }

    @Override
    public void onClose() {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void computeLayout() {
        this.left = (this.width - SCREEN_WIDTH) / 2;
        this.top = (this.height - SCREEN_HEIGHT) / 2;
        this.contentX = this.left + PADDING;
        this.contentY = this.top + HEADER_HEIGHT + PADDING;
        this.contentW = SCREEN_WIDTH - (PADDING * 2);
        this.contentH = SCREEN_HEIGHT - HEADER_HEIGHT - (PADDING * 2);
    }

    private void refreshSections() {
        List<ToolCatalogEntry> builtInTools = new ArrayList<>();
        Map<String, List<ToolCatalogEntry>> extensionToolsByGroup = new LinkedHashMap<>();
        List<ToolCatalogEntry> mcpTools = new ArrayList<>();

        for (ToolCatalogEntry tool : ClientToolCatalog.get()) {
            if (tool == null || tool.toolName() == null || tool.toolName().isBlank()) {
                continue;
            }
            ToolSectionDescriptor descriptor = describeSection(tool);
            switch (descriptor.kind()) {
                case BUILT_IN -> builtInTools.add(tool);
                case EXTENSION -> extensionToolsByGroup
                        .computeIfAbsent(descriptor.label(), ignored -> new ArrayList<>())
                        .add(tool);
                case MCP -> mcpTools.add(tool);
            }
        }

        Comparator<ToolCatalogEntry> comparator = Comparator.comparing(ToolCatalogEntry::toolName);
        builtInTools.sort(comparator);
        mcpTools.sort(comparator);

        List<ToolSection> refreshedSections = new ArrayList<>();
        refreshedSections.add(new ToolSection(ToolSectionKind.BUILT_IN, "Built-in", builtInTools));
        extensionToolsByGroup.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    entry.getValue().sort(comparator);
                    refreshedSections.add(new ToolSection(ToolSectionKind.EXTENSION, entry.getKey(), entry.getValue()));
                });
        refreshedSections.add(new ToolSection(ToolSectionKind.MCP, "MCP", mcpTools));

        this.sections = List.copyOf(refreshedSections);
        this.lines = buildLines(this.sections);
        int visibleLines = Math.max(1, this.contentH / lineStep());
        this.maxScrollLines = Math.max(0, this.lines.size() - visibleLines);
        this.scrollOffsetLines = clamp(this.scrollOffsetLines, 0, this.maxScrollLines);
    }

    private void renderFrame(GuiGraphics guiGraphics) {
        guiGraphics.fill(this.left, this.top, this.left + SCREEN_WIDTH, this.top + SCREEN_HEIGHT, COLOR_BG_DARK);
        guiGraphics.renderOutline(this.left, this.top, SCREEN_WIDTH, SCREEN_HEIGHT, COLOR_BORDER);
        guiGraphics.renderOutline(this.left + 1, this.top + 1, SCREEN_WIDTH - 2, SCREEN_HEIGHT - 2, COLOR_PRIMARY_FLUIX_DIM);

        guiGraphics.fill(this.left, this.top, this.left + SCREEN_WIDTH, this.top + HEADER_HEIGHT, COLOR_BG_PANEL_TRANSPARENT);
        guiGraphics.fill(this.left, this.top + HEADER_HEIGHT - 1, this.left + SCREEN_WIDTH, this.top + HEADER_HEIGHT, COLOR_BORDER);

        guiGraphics.fill(this.contentX, this.contentY, this.contentX + this.contentW, this.contentY + this.contentH, COLOR_BG_PANEL);
        guiGraphics.renderOutline(this.contentX, this.contentY, this.contentW, this.contentH, COLOR_BORDER);
        guiGraphics.fill(this.contentX, this.contentY + PANEL_HEADER_HEIGHT, this.contentX + this.contentW, this.contentY + PANEL_HEADER_HEIGHT + 1, COLOR_BORDER);
    }

    private void renderText(GuiGraphics guiGraphics) {
        drawScaledString(guiGraphics, "STATUS", this.left + PADDING, this.top + 9, COLOR_TEXT_MAIN, true);
        drawScaledString(guiGraphics, "TOOLS: " + totalToolCount(), this.contentX + PADDING, this.contentY + 5, COLOR_TEXT_DIM, false);

        int textY = this.contentY + PANEL_HEADER_HEIGHT + CONTENT_GAP;
        int startIndex = this.scrollOffsetLines;
        int maxVisible = Math.max(1, (this.contentH - PANEL_HEADER_HEIGHT - CONTENT_GAP) / lineStep());
        int endIndex = Math.min(this.lines.size(), startIndex + maxVisible);
        int textMaxWidth = Math.max(1, Math.round((this.contentW - (PADDING * 2)) / FONT_SCALE));

        for (int i = startIndex; i < endIndex; i++) {
            StatusLine line = this.lines.get(i);
            String trimmed = this.font.plainSubstrByWidth(line.text(), textMaxWidth);
            drawScaledString(guiGraphics, trimmed, this.contentX + PADDING, textY, line.color(), false);
            textY += lineStep();
        }
    }

    private List<StatusLine> buildLines(List<ToolSection> sections) {
        List<StatusLine> builtLines = new ArrayList<>();
        for (ToolSection section : sections) {
            builtLines.add(new StatusLine(section.label() + " (" + section.tools().size() + ")", COLOR_ACCENT_CYAN));
            if (section.tools().isEmpty()) {
                builtLines.add(new StatusLine("- none loaded", COLOR_TEXT_DIM));
                continue;
            }
            for (ToolCatalogEntry tool : section.tools()) {
                builtLines.add(new StatusLine("- " + tool.toolName(), COLOR_TEXT_MAIN));
                builtLines.add(new StatusLine("  " + textOrFallback(tool.description(), "No description."), COLOR_TEXT_DIM));
            }
        }
        return builtLines;
    }

    private ToolSection firstSection(ToolSectionKind kind) {
        for (ToolSection section : this.sections) {
            if (section.kind() == kind) {
                return section;
            }
        }
        return new ToolSection(kind, defaultLabel(kind), List.of());
    }

    private List<ToolCatalogEntry> toolsForKind(ToolSectionKind kind) {
        List<ToolCatalogEntry> tools = new ArrayList<>();
        for (ToolSection section : this.sections) {
            if (section.kind() != kind) {
                continue;
            }
            tools.addAll(section.tools());
        }
        return List.copyOf(tools);
    }

    private List<String> sectionLabels() {
        List<String> labels = new ArrayList<>();
        for (ToolSection section : this.sections) {
            labels.add(section.label());
        }
        return List.copyOf(labels);
    }

    private int totalToolCount() {
        int count = 0;
        for (ToolSection section : this.sections) {
            count += section.tools().size();
        }
        return count;
    }

    private boolean isWithinContent(double mouseX, double mouseY) {
        return mouseX >= this.contentX && mouseX < this.contentX + this.contentW
                && mouseY >= this.contentY && mouseY < this.contentY + this.contentH;
    }

    private static ToolSectionDescriptor describeSection(ToolCatalogEntry tool) {
        String providerId = tool.providerId();
        String groupId = tool.groupId();
        String toolName = tool.toolName();

        if (providerId != null && !providerId.isBlank()) {
            if (providerId.startsWith("mcp.runtime.")) {
                return new ToolSectionDescriptor(ToolSectionKind.MCP, "MCP");
            }
            if ("mc".equals(providerId) || "http".equals(providerId)) {
                return new ToolSectionDescriptor(ToolSectionKind.BUILT_IN, "Built-in");
            }
            return new ToolSectionDescriptor(ToolSectionKind.EXTENSION, extensionLabel(groupId, providerId));
        }

        if (toolName == null || toolName.isBlank()) {
            return new ToolSectionDescriptor(ToolSectionKind.EXTENSION, extensionLabel(groupId, providerId));
        }
        if (toolName.startsWith("mcp.")) {
            return new ToolSectionDescriptor(ToolSectionKind.MCP, "MCP");
        }
        if (toolName.startsWith("mc.") || toolName.startsWith("http") || "response".equals(toolName)) {
            return new ToolSectionDescriptor(ToolSectionKind.BUILT_IN, "Built-in");
        }
        return new ToolSectionDescriptor(ToolSectionKind.EXTENSION, extensionLabel(groupId, providerId));
    }

    private static String extensionLabel(String groupId, String providerId) {
        if (groupId != null && !groupId.isBlank()) {
            return groupId;
        }
        if (providerId != null && !providerId.isBlank()) {
            return providerId;
        }
        return "extension";
    }

    private static String defaultLabel(ToolSectionKind kind) {
        return switch (kind) {
            case BUILT_IN -> "Built-in";
            case EXTENSION -> "extension";
            case MCP -> "MCP";
        };
    }

    private static String textOrFallback(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        return text;
    }

    private static List<String> toolNames(List<ToolCatalogEntry> tools) {
        List<String> names = new ArrayList<>();
        for (ToolCatalogEntry tool : tools) {
            if (tool == null || tool.toolName() == null || tool.toolName().isBlank()) {
                continue;
            }
            names.add(tool.toolName());
        }
        return List.copyOf(names);
    }

    private int lineStep() {
        return Math.max(1, Math.round(this.font.lineHeight * FONT_SCALE)) + LINE_SPACING;
    }

    private void drawScaledString(GuiGraphics guiGraphics, String text, int x, int y, int color, boolean title) {
        if (text == null || text.isEmpty()) {
            return;
        }
        float scale = title ? TITLE_FONT_SCALE : FONT_SCALE;
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(this.font, text, Math.round(x / scale), Math.round(y / scale), color, false);
        guiGraphics.pose().popPose();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private enum ToolSectionKind {
        BUILT_IN,
        EXTENSION,
        MCP
    }

    private record ToolSectionDescriptor(ToolSectionKind kind, String label) {
    }

    private record ToolSection(ToolSectionKind kind, String label, List<ToolCatalogEntry> tools) {
        private ToolSection {
            label = label == null || label.isBlank() ? defaultLabel(kind) : label;
            tools = tools == null ? List.of() : List.copyOf(tools);
        }
    }

    private record StatusLine(String text, int color) {
    }
}

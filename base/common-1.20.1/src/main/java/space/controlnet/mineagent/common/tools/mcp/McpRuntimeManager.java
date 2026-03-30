package space.controlnet.mineagent.common.tools.mcp;

import dev.architectury.platform.Platform;
import net.minecraft.server.MinecraftServer;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.common.tools.mcp.McpSchemaMapper.McpProjectedTool;
import space.controlnet.mineagent.common.tools.mcp.McpSchemaMapper.McpRemoteTool;
import space.controlnet.mineagent.core.tools.mcp.McpConfig;
import space.controlnet.mineagent.core.tools.mcp.McpServerConfig;
import space.controlnet.mineagent.core.tools.mcp.McpTransportKind;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public final class McpRuntimeManager {
    private static final Object STATE_LOCK = new Object();
    private static final AtomicLong RELOAD_GENERATION = new AtomicLong();
    private static final ExecutorService RELOAD_EXECUTOR = Executors.newSingleThreadExecutor(
            new NamedThreadFactory("mineagent-mcp-runtime")
    );

    private static final SessionFactory DEFAULT_SESSION_FACTORY = new SessionFactory() {
        @Override
        public McpClientSession open(String serverAlias, McpServerConfig serverConfig) throws McpTransportException {
            McpTransportKind transportKind = serverConfig == null ? null : serverConfig.type();
            if (transportKind == McpTransportKind.STDIO) {
                return new StdioSessionAdapter(McpStdioClientSession.open(serverAlias, serverConfig));
            }
            if (transportKind == McpTransportKind.HTTP) {
                return new HttpSessionAdapter(McpHttpClientSession.open(serverAlias, serverConfig));
            }
            throw McpTransportException.executionFailed(new IllegalStateException(
                    "Unsupported MCP transport for server '" + serverAlias + "'."
            ));
        }
    };

    private static Map<String, ActiveRuntime> activeRuntimesByAlias = Map.of();

    private McpRuntimeManager() {
    }

    public static void reload(MinecraftServer server) {
        reload(Platform.getConfigFolder(), DEFAULT_SESSION_FACTORY);
    }

    static void reload(java.nio.file.Path configRoot) {
        reload(configRoot, DEFAULT_SESSION_FACTORY);
    }

    static void reload(java.nio.file.Path configRoot, SessionFactory sessionFactory) {
        Objects.requireNonNull(configRoot, "configRoot");
        Objects.requireNonNull(sessionFactory, "sessionFactory");

        long generation = RELOAD_GENERATION.incrementAndGet();
        RELOAD_EXECUTOR.execute(() -> performReload(generation, configRoot, sessionFactory));
    }

    public static void clear() {
        RELOAD_GENERATION.incrementAndGet();

        List<ActiveRuntime> runtimesToClose;
        synchronized (STATE_LOCK) {
            runtimesToClose = new ArrayList<>(activeRuntimesByAlias.values());
            activeRuntimesByAlias = Map.of();

            for (ActiveRuntime runtime : runtimesToClose) {
                ToolRegistry.unregister(providerId(runtime.serverAlias()));
            }
        }

        closeRuntimes(runtimesToClose, "clear");
    }

    static List<String> activeAliases() {
        synchronized (STATE_LOCK) {
            return List.copyOf(activeRuntimesByAlias.keySet());
        }
    }

    static void awaitIdle(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        Future<?> future = RELOAD_EXECUTOR.submit(() -> {
        });
        try {
            future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for MCP runtime reload work.", interruptedException);
        } catch (ExecutionException | TimeoutException exception) {
            throw new AssertionError("Timed out while waiting for MCP runtime reload work.", exception);
        }
    }

    private static void performReload(long generation, java.nio.file.Path configRoot, SessionFactory sessionFactory) {
        McpConfigLoader.LoadResult loadResult = McpConfigLoader.loadResult(configRoot);
        if (!loadResult.loadedFromDisk() && hasActiveRuntimes()) {
            MineAgent.LOGGER.warn(
                    "Skipping MCP runtime reload because {} fell back to defaults. Keeping active aliases {}.",
                    loadResult.path(),
                    activeAliases()
            );
            return;
        }

        McpConfig config = loadResult.config();
        LinkedHashSet<String> configuredAliases = new LinkedHashSet<>(config.mcpServers().keySet());
        Map<String, LoadedRuntime> discoveredRuntimes = discoverHealthyRuntimes(config, sessionFactory);

        applyDiscoveredRuntimes(generation, configuredAliases, discoveredRuntimes);
    }

    private static Map<String, LoadedRuntime> discoverHealthyRuntimes(McpConfig config, SessionFactory sessionFactory) {
        LinkedHashMap<String, LoadedRuntime> discoveredRuntimes = new LinkedHashMap<>();
        for (Map.Entry<String, McpServerConfig> entry : config.mcpServers().entrySet()) {
            String serverAlias = entry.getKey();
            McpServerConfig serverConfig = entry.getValue();

            McpClientSession session = null;
            try {
                session = sessionFactory.open(serverAlias, serverConfig);
                List<McpRemoteTool> remoteTools = session.listTools();
                List<McpProjectedTool> projectedTools = remoteTools.stream()
                        .map(remoteTool -> McpSchemaMapper.project(serverAlias, remoteTool))
                        .toList();
                discoveredRuntimes.put(serverAlias, new LoadedRuntime(serverAlias, session, projectedTools));
            } catch (Exception exception) {
                closeSession(session, "failed MCP runtime discovery for alias '" + serverAlias + "'");
                MineAgent.LOGGER.warn(
                        "Failed to reload MCP server '{}'. Keeping previous runtime if present.",
                        serverAlias,
                        exception
                );
            }
        }
        return Collections.unmodifiableMap(discoveredRuntimes);
    }

    private static void applyDiscoveredRuntimes(long generation, Set<String> configuredAliases,
                                                Map<String, LoadedRuntime> discoveredRuntimes) {
        List<ActiveRuntime> runtimesToClose = new ArrayList<>();
        List<String> aliasesToUnregister = new ArrayList<>();
        List<ActiveRuntime> runtimesToRegister = new ArrayList<>();
        List<LoadedRuntime> discardedDiscoveries = new ArrayList<>();

        synchronized (STATE_LOCK) {
            if (generation != RELOAD_GENERATION.get()) {
                discardedDiscoveries.addAll(discoveredRuntimes.values());
            } else {
                LinkedHashMap<String, ActiveRuntime> nextRuntimes = new LinkedHashMap<>();
                Map<String, ActiveRuntime> previousRuntimes = activeRuntimesByAlias;

                for (String configuredAlias : configuredAliases) {
                    LoadedRuntime discoveredRuntime = discoveredRuntimes.get(configuredAlias);
                    ActiveRuntime previousRuntime = previousRuntimes.get(configuredAlias);

                    if (discoveredRuntime != null) {
                        ActiveRuntime nextRuntime = discoveredRuntime.toActiveRuntime();
                        nextRuntimes.put(configuredAlias, nextRuntime);
                        runtimesToRegister.add(nextRuntime);
                        if (previousRuntime != null) {
                            runtimesToClose.add(previousRuntime);
                        }
                        continue;
                    }

                    if (previousRuntime != null) {
                        nextRuntimes.put(configuredAlias, previousRuntime);
                    }
                }

                for (Map.Entry<String, ActiveRuntime> entry : previousRuntimes.entrySet()) {
                    String previousAlias = entry.getKey();
                    if (configuredAliases.contains(previousAlias)) {
                        continue;
                    }
                    aliasesToUnregister.add(previousAlias);
                    runtimesToClose.add(entry.getValue());
                }

                for (String aliasToUnregister : aliasesToUnregister) {
                    ToolRegistry.unregister(providerId(aliasToUnregister));
                }
                for (ActiveRuntime runtimeToRegister : runtimesToRegister) {
                    ToolRegistry.registerOrReplace(providerId(runtimeToRegister.serverAlias()), runtimeToRegister.provider());
                }

                activeRuntimesByAlias = unmodifiableLinkedMap(nextRuntimes);
            }
        }

        closeLoadedRuntimes(discardedDiscoveries, "discarded stale MCP reload generation");
        closeRuntimes(runtimesToClose, "replaced or removed MCP runtime");
    }

    private static boolean hasActiveRuntimes() {
        synchronized (STATE_LOCK) {
            return !activeRuntimesByAlias.isEmpty();
        }
    }

    private static Map<String, ActiveRuntime> unmodifiableLinkedMap(Map<String, ActiveRuntime> runtimesByAlias) {
        if (runtimesByAlias == null || runtimesByAlias.isEmpty()) {
            return Map.of();
        }

        LinkedHashMap<String, ActiveRuntime> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ActiveRuntime> entry : runtimesByAlias.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String providerId(String serverAlias) {
        return "mcp.runtime." + serverAlias;
    }

    private static void closeRuntimes(Collection<ActiveRuntime> runtimes, String reason) {
        if (runtimes == null || runtimes.isEmpty()) {
            return;
        }
        for (ActiveRuntime runtime : runtimes) {
            if (runtime == null) {
                continue;
            }
            closeProvider(runtime.provider(), reason + " for alias '" + runtime.serverAlias() + "'");
        }
    }

    private static void closeLoadedRuntimes(Collection<LoadedRuntime> runtimes, String reason) {
        if (runtimes == null || runtimes.isEmpty()) {
            return;
        }
        for (LoadedRuntime runtime : runtimes) {
            if (runtime == null) {
                continue;
            }
            closeSession(runtime.session(), reason + " for alias '" + runtime.serverAlias() + "'");
        }
    }

    private static void closeProvider(McpToolProvider provider, String reason) {
        if (provider == null) {
            return;
        }
        try {
            provider.close();
        } catch (Exception exception) {
            MineAgent.LOGGER.warn("Failed to close {}.", reason, exception);
        }
    }

    private static void closeSession(McpClientSession session, String reason) {
        if (session == null) {
            return;
        }
        try {
            session.close();
        } catch (Exception exception) {
            MineAgent.LOGGER.warn("Failed to close {}.", reason, exception);
        }
    }

    @FunctionalInterface
    interface SessionFactory {
        McpClientSession open(String serverAlias, McpServerConfig serverConfig) throws McpTransportException;
    }

    private record LoadedRuntime(String serverAlias, McpClientSession session, List<McpProjectedTool> projectedTools) {
        private LoadedRuntime {
            if (serverAlias == null || serverAlias.isBlank()) {
                throw new IllegalArgumentException("serverAlias is required.");
            }
            session = Objects.requireNonNull(session, "session");
            projectedTools = projectedTools == null ? List.of() : List.copyOf(projectedTools);
        }

        private ActiveRuntime toActiveRuntime() {
            return new ActiveRuntime(serverAlias, new McpToolProvider(serverAlias, session, projectedTools));
        }
    }

    private record ActiveRuntime(String serverAlias, McpToolProvider provider) {
        private ActiveRuntime {
            if (serverAlias == null || serverAlias.isBlank()) {
                throw new IllegalArgumentException("serverAlias is required.");
            }
            provider = Objects.requireNonNull(provider, "provider");
        }
    }

    private record StdioSessionAdapter(McpStdioClientSession delegate) implements McpClientSession {
        private StdioSessionAdapter {
            delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public List<McpRemoteTool> listTools() throws McpTransportException {
            return delegate.listTools();
        }

        @Override
        public com.google.gson.JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException {
            return delegate.callTool(remoteToolName, argumentsJson);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private record HttpSessionAdapter(McpHttpClientSession delegate) implements McpClientSession {
        private HttpSessionAdapter {
            delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public List<McpRemoteTool> listTools() throws McpTransportException {
            return delegate.listTools();
        }

        @Override
        public com.google.gson.JsonObject callTool(String remoteToolName, String argumentsJson) throws McpTransportException {
            return delegate.callTool(remoteToolName, argumentsJson);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        private NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}

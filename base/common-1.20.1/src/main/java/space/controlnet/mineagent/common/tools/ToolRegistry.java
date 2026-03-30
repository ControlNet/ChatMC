package space.controlnet.mineagent.common.tools;

import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tool providers and tool specs.
 */
public final class ToolRegistry {
    private static final Map<String, ToolProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, String> PROVIDER_IDS_BY_TOOL = new ConcurrentHashMap<>();
    private static final Map<String, Set<String>> TOOL_NAMES_BY_PROVIDER = new ConcurrentHashMap<>();
    private static final Map<String, AgentTool> TOOLS_BY_NAME = new ConcurrentHashMap<>();
    private static volatile List<AgentTool> toolSpecsSnapshot = List.of();

    private ToolRegistry() {
    }

    public static synchronized void register(String providerId, ToolProvider provider) {
        registerOrReplace(providerId, provider);
    }

    public static synchronized void registerOrReplace(String providerId, ToolProvider provider) {
        if (providerId == null || providerId.isBlank() || provider == null) {
            return;
        }

        removeOwnedTools(providerId);
        PROVIDERS.put(providerId, provider);

        Map<String, AgentTool> providerTools = collectProviderTools(provider);
        Set<String> ownedToolNames = new HashSet<>();

        for (Map.Entry<String, AgentTool> entry : providerTools.entrySet()) {
            String toolName = entry.getKey();
            AgentTool tool = entry.getValue();

            String previousOwnerId = PROVIDER_IDS_BY_TOOL.put(toolName, providerId);
            if (previousOwnerId != null && !previousOwnerId.equals(providerId)) {
                removeOwnedToolName(previousOwnerId, toolName);
            }

            TOOLS_BY_NAME.put(toolName, tool);
            ownedToolNames.add(toolName);
        }

        TOOL_NAMES_BY_PROVIDER.put(providerId, ownedToolNames);
        rebuildSnapshot();
    }

    public static synchronized void unregister(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return;
        }

        PROVIDERS.remove(providerId);
        removeOwnedTools(providerId);
        rebuildSnapshot();
    }

    public static synchronized void unregisterByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return;
        }

        List<String> toolNames = new ArrayList<>(TOOLS_BY_NAME.keySet());
        for (String toolName : toolNames) {
            if (toolName == null || !toolName.startsWith(prefix)) {
                continue;
            }

            String providerId = PROVIDER_IDS_BY_TOOL.remove(toolName);
            TOOLS_BY_NAME.remove(toolName);
            if (providerId != null) {
                removeOwnedToolName(providerId, toolName);
            }
        }

        rebuildSnapshot();
    }

    private static Map<String, AgentTool> collectProviderTools(ToolProvider provider) {
        Map<String, AgentTool> providerTools = new LinkedHashMap<>();
        for (AgentTool tool : provider.specs()) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                continue;
            }
            providerTools.put(tool.name(), tool);
        }
        return providerTools;
    }

    public static List<AgentTool> getToolSpecs() {
        return toolSpecsSnapshot;
    }

    public static AgentTool getToolSpec(String name) {
        if (name == null) {
            return null;
        }
        return TOOLS_BY_NAME.get(name);
    }

    public static ToolProvider.ExecutionAffinity getExecutionAffinity(String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return ToolProvider.ExecutionAffinity.SERVER_THREAD;
        }

        String providerId = PROVIDER_IDS_BY_TOOL.get(toolName);
        ToolProvider provider = providerId == null ? null : PROVIDERS.get(providerId);
        if (provider == null) {
            return ToolProvider.ExecutionAffinity.SERVER_THREAD;
        }

        ToolProvider.ExecutionAffinity affinity = provider.executionAffinity();
        return affinity == null ? ToolProvider.ExecutionAffinity.SERVER_THREAD : affinity;
    }

    public static ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        if (call == null || call.toolName() == null) {
            return ToolOutcome.result(ToolResult.error("invalid_tool", "Missing tool"));
        }
        AgentTool tool = TOOLS_BY_NAME.get(call.toolName());
        if (tool == null) {
            return ToolOutcome.result(ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName()));
        }
        String providerId = PROVIDER_IDS_BY_TOOL.get(call.toolName());
        ToolProvider provider = providerId == null ? null : PROVIDERS.get(providerId);
        if (provider != null) {
            return provider.execute(terminal, call, approved);
        }
        MineAgent.LOGGER.warn("No provider found for tool {}", call.toolName());
        return ToolOutcome.result(ToolResult.error("unknown_tool", "No provider for tool: " + call.toolName()));
    }

    private static void removeOwnedTools(String providerId) {
        Set<String> toolNames = TOOL_NAMES_BY_PROVIDER.remove(providerId);
        if (toolNames == null || toolNames.isEmpty()) {
            return;
        }

        for (String toolName : toolNames) {
            if (!providerId.equals(PROVIDER_IDS_BY_TOOL.get(toolName))) {
                continue;
            }
            PROVIDER_IDS_BY_TOOL.remove(toolName);
            TOOLS_BY_NAME.remove(toolName);
        }
    }

    private static void removeOwnedToolName(String providerId, String toolName) {
        Set<String> toolNames = TOOL_NAMES_BY_PROVIDER.get(providerId);
        if (toolNames == null) {
            return;
        }

        toolNames.remove(toolName);
        if (toolNames.isEmpty()) {
            TOOL_NAMES_BY_PROVIDER.remove(providerId, toolNames);
        }
    }

    private static void rebuildSnapshot() {
        toolSpecsSnapshot = TOOLS_BY_NAME.values().stream()
                .sorted(Comparator.comparing(AgentTool::name))
                .toList();
    }
}

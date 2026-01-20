package space.controlnet.chatmc.common.tools;

import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for tool providers and tool specs.
 */
public final class ToolRegistry {
    private static final Map<String, ToolProvider> PROVIDERS = new ConcurrentHashMap<>();
    private static final Map<String, ToolProvider> PROVIDERS_BY_TOOL = new ConcurrentHashMap<>();
    private static final Map<String, AgentTool> TOOLS_BY_NAME = new ConcurrentHashMap<>();
    private static final List<AgentTool> TOOL_SPECS = new ArrayList<>();

    private ToolRegistry() {
    }

    public static synchronized void register(String providerId, ToolProvider provider) {
        if (providerId == null || providerId.isBlank() || provider == null) {
            return;
        }
        PROVIDERS.put(providerId, provider);
        for (AgentTool tool : provider.specs()) {
            if (tool == null || tool.name() == null || tool.name().isBlank()) {
                continue;
            }
            AgentTool previous = TOOLS_BY_NAME.put(tool.name(), tool);
            PROVIDERS_BY_TOOL.put(tool.name(), provider);
            if (previous == null) {
                TOOL_SPECS.add(tool);
            } else {
                int index = TOOL_SPECS.indexOf(previous);
                if (index >= 0) {
                    TOOL_SPECS.set(index, tool);
                }
            }
        }
    }

    public static List<AgentTool> getToolSpecs() {
        return Collections.unmodifiableList(TOOL_SPECS);
    }

    public static AgentTool getToolSpec(String name) {
        if (name == null) {
            return null;
        }
        return TOOLS_BY_NAME.get(name);
    }

    public static ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        if (call == null || call.toolName() == null) {
            return ToolOutcome.result(ToolResult.error("invalid_tool", "Missing tool"));
        }
        AgentTool tool = TOOLS_BY_NAME.get(call.toolName());
        if (tool == null) {
            return ToolOutcome.result(ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName()));
        }
        ToolProvider provider = PROVIDERS_BY_TOOL.get(call.toolName());
        if (provider != null) {
            return provider.execute(terminal, call, approved);
        }
        ChatMC.LOGGER.warn("No provider found for tool {}", call.toolName());
        return ToolOutcome.result(ToolResult.error("unknown_tool", "No provider for tool: " + call.toolName()));
    }
}

package space.controlnet.mineagent.common.agent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.MineAgentNetwork;
import space.controlnet.mineagent.common.llm.PromptRuntime;
import space.controlnet.mineagent.common.terminal.TerminalContextRegistry;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.common.tools.ToolRegistry;
import space.controlnet.mineagent.core.agent.AgentPlayerContext;
import space.controlnet.mineagent.core.agent.AgentSessionContext;
import space.controlnet.mineagent.core.agent.PromptId;
import space.controlnet.mineagent.core.session.ChatMessage;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class McSessionContext implements AgentSessionContext, Serializable {
    private static final long serialVersionUID = 1L;
    private static final long TOOL_EXECUTION_TIMEOUT_MS = 30_000L;

    private final UUID playerId;

    public McSessionContext(UUID playerId) {
        this.playerId = playerId;
    }

    @Override
    public Optional<SessionSnapshot> getSession(UUID sessionId) {
        return MineAgentNetwork.SESSIONS.get(sessionId);
    }

    @Override
    public void appendMessage(UUID sessionId, ChatMessage message) {
        MineAgentNetwork.appendMessageAndBroadcast(sessionId, message);
    }

    @Override
    public Optional<TerminalContext> getTerminal(AgentPlayerContext playerCtx) {
        return MineAgentNetwork.findPlayer(playerId)
                .flatMap(TerminalContextRegistry::fromPlayer);
    }

    @Override
    public ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        ToolProvider.ExecutionAffinity affinity = ToolRegistry.getExecutionAffinity(
                call == null ? null : call.toolName());
        if (affinity == ToolProvider.ExecutionAffinity.CALLING_THREAD) {
            return ToolRegistry.executeTool(terminal, call, approved);
        }

        MinecraftServer server = MineAgentNetwork.findPlayer(playerId)
                .map(ServerPlayer::getServer)
                .orElse(null);
        if (server == null) {
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        }

        if (server.isSameThread()) {
            return ToolRegistry.executeTool(terminal, call, approved);
        }

        CompletableFuture<ToolOutcome> outcomeFuture = new CompletableFuture<>();
        try {
            server.execute(() -> {
                try {
                    outcomeFuture.complete(ToolRegistry.executeTool(terminal, call, approved));
                } catch (Throwable throwable) {
                    outcomeFuture.completeExceptionally(throwable);
                }
            });
        } catch (RuntimeException queueFailure) {
            MineAgent.LOGGER.warn("Failed to queue tool execution on server thread", queueFailure);
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        }

        try {
            return outcomeFuture.get(TOOL_EXECUTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            MineAgent.LOGGER.warn("Timed out executing tool {} on server thread",
                    call != null ? call.toolName() : "<unknown>", timeoutException);
            return ToolOutcome.result(ToolResult.error("tool_timeout", "tool execution timeout"));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        } catch (ExecutionException executionException) {
            MineAgent.LOGGER.warn("Tool execution failed on server thread", executionException.getCause());
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        }
    }

    @Override
    public java.util.List<space.controlnet.mineagent.core.tools.AgentTool> getToolSpecs() {
        return ToolRegistry.getToolSpecs();
    }

    @Override
    public String renderPrompt(PromptId promptId, String locale, Map<String, String> variables) {
        return PromptRuntime.render(promptId, locale, variables);
    }

    @Override
    public void logDebug(String message, Object... args) {
        MineAgent.LOGGER.debug(message, args);
    }

    @Override
    public void logError(String message, Throwable error) {
        MineAgent.LOGGER.error(message, error);
    }
}

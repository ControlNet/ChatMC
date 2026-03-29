package space.controlnet.chatmc.common.agent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.llm.PromptRuntime;
import space.controlnet.chatmc.common.terminal.TerminalContextRegistry;
import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.core.agent.AgentPlayerContext;
import space.controlnet.chatmc.core.agent.AgentSessionContext;
import space.controlnet.chatmc.core.agent.PromptId;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolResult;

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
        return ChatMCNetwork.SESSIONS.get(sessionId);
    }

    @Override
    public void appendMessage(UUID sessionId, ChatMessage message) {
        ChatMCNetwork.appendMessageAndBroadcast(sessionId, message);
    }

    @Override
    public Optional<TerminalContext> getTerminal(AgentPlayerContext playerCtx) {
        return ChatMCNetwork.findPlayer(playerId)
                .flatMap(TerminalContextRegistry::fromPlayer);
    }

    @Override
    public ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        ToolProvider.ExecutionAffinity affinity = ToolRegistry.getExecutionAffinity(
                call == null ? null : call.toolName());
        if (affinity == ToolProvider.ExecutionAffinity.CALLING_THREAD) {
            return ToolRegistry.executeTool(terminal, call, approved);
        }

        MinecraftServer server = ChatMCNetwork.findPlayer(playerId)
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
            ChatMC.LOGGER.warn("Failed to queue tool execution on server thread", queueFailure);
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        }

        try {
            return outcomeFuture.get(TOOL_EXECUTION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeoutException) {
            ChatMC.LOGGER.warn("Timed out executing tool {} on server thread",
                    call != null ? call.toolName() : "<unknown>", timeoutException);
            return ToolOutcome.result(ToolResult.error("tool_timeout", "tool execution timeout"));
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        } catch (ExecutionException executionException) {
            ChatMC.LOGGER.warn("Tool execution failed on server thread", executionException.getCause());
            return ToolOutcome.result(ToolResult.error("tool_execution_failed", "tool execution failed"));
        }
    }

    @Override
    public java.util.List<space.controlnet.chatmc.core.tools.AgentTool> getToolSpecs() {
        return ToolRegistry.getToolSpecs();
    }

    @Override
    public String renderPrompt(PromptId promptId, String locale, Map<String, String> variables) {
        return PromptRuntime.render(promptId, locale, variables);
    }

    @Override
    public void logDebug(String message, Object... args) {
        ChatMC.LOGGER.debug(message, args);
    }

    @Override
    public void logError(String message, Throwable error) {
        ChatMC.LOGGER.error(message, error);
    }
}

package space.controlnet.chatmc.common.agent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.common.ChatMCNetwork;
import space.controlnet.chatmc.common.audit.AuditLogger;
import space.controlnet.chatmc.common.llm.PromptRuntime;
import space.controlnet.chatmc.common.terminal.TerminalContextRegistry;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.core.agent.AgentLoop;
import space.controlnet.chatmc.core.agent.AgentLoopResult;
import space.controlnet.chatmc.core.agent.AgentPlayerContext;
import space.controlnet.chatmc.core.agent.AgentSessionContext;
import space.controlnet.chatmc.core.agent.Logger;
import space.controlnet.chatmc.core.agent.PromptId;
import space.controlnet.chatmc.core.session.ChatMessage;
import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.session.TerminalBinding;
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

/**
 * MC-specific wrapper for the core AgentLoop.
 * Provides MC-specific implementations of the agent context interfaces.
 */
public final class AgentRunner {
    private final AgentLoop agentLoop;

    public AgentRunner() {
        Logger logWarning = new Logger() {
            @Override
            public void warn(String message, Throwable exception) {
                ChatMC.LOGGER.warn(message, exception);
            }

            @Override
            public void debug(String message) {
                ChatMC.LOGGER.debug(message);
            }
        };
        this.agentLoop = new AgentLoop(AuditLogger.instance(), logWarning);
    }

    /**
     * Update the rate limiter cooldown.
     */
    public void setRateLimitCooldown(long cooldownMs) {
        agentLoop.setRateLimitCooldown(cooldownMs);
    }

    /**
     * Update the LLM timeout.
     */
    public void setTimeoutMs(long timeoutMs) {
        agentLoop.setTimeoutMs(timeoutMs);
    }

    /**
     * Update the max tool calls per decision.
     */
    public void setMaxToolCalls(int maxToolCalls) {
        agentLoop.setMaxToolCalls(maxToolCalls);
    }

    /**
     * Update the max iterations per request.
     */
    public void setMaxIterations(int maxIterations) {
        agentLoop.setMaxIterations(maxIterations);
    }

    /**
     * Update whether raw LLM responses are logged.
     */
    public void setLogResponses(boolean logResponses) {
        agentLoop.setLogResponses(logResponses);
    }

    /**
     * Update the max retry count for transient LLM errors.
     */
    public void setMaxRetries(int maxRetries) {
        agentLoop.setMaxRetries(maxRetries);
    }

    /**
     * Run the agent loop for a user message.
     */
    public AgentLoopResult runLoop(ServerPlayer player, UUID sessionId, TerminalBinding binding, String effectiveLocale) {
        AgentPlayerContext playerContext = new McPlayerContext(player.getUUID(), player.getGameProfile().getName());
        AgentSessionContext sessionContext = new McSessionContext(player.getUUID());
        return agentLoop.runLoop(playerContext, sessionId, binding, effectiveLocale, sessionContext);
    }

    /**
     * MC-specific player context implementation.
     */
    private record McPlayerContext(UUID playerId, String playerName) implements AgentPlayerContext, Serializable {
        private static final long serialVersionUID = 1L;
        @Override
        public UUID getPlayerId() {
            return playerId;
        }

        @Override
        public String getPlayerName() {
            return playerName;
        }
    }

    /**
     * MC-specific session context implementation.
     */
    private static final class McSessionContext implements AgentSessionContext, Serializable {
        private static final long serialVersionUID = 1L;
        private static final long TOOL_EXECUTION_TIMEOUT_MS = 30_000L;
        private final UUID playerId;

        McSessionContext(UUID playerId) {
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
}

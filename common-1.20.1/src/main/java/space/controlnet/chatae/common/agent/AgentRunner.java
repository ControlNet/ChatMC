package space.controlnet.chatae.common.agent;

import net.minecraft.server.level.ServerPlayer;
import space.controlnet.chatae.common.ChatAE;
import space.controlnet.chatae.common.ChatAENetwork;
import space.controlnet.chatae.common.audit.AuditLogger;
import space.controlnet.chatae.common.llm.PromptRuntime;
import space.controlnet.chatae.common.terminal.TerminalContextFactory;
import space.controlnet.chatae.common.tools.ToolRouter;
import space.controlnet.chatae.core.agent.AgentLoop;
import space.controlnet.chatae.core.agent.AgentLoopResult;
import space.controlnet.chatae.core.agent.AgentPlayerContext;
import space.controlnet.chatae.core.agent.AgentSessionContext;
import space.controlnet.chatae.core.agent.Logger;
import space.controlnet.chatae.core.agent.PromptId;
import space.controlnet.chatae.core.session.ChatMessage;
import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.session.TerminalBinding;
import space.controlnet.chatae.core.terminal.TerminalContext;
import space.controlnet.chatae.core.tools.ToolCall;
import space.controlnet.chatae.core.tools.ToolOutcome;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
                ChatAE.LOGGER.warn(message, exception);
            }

            @Override
            public void debug(String message) {
                ChatAE.LOGGER.debug(message);
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
     * Update the max history messages in prompts.
     */
    public void setMaxHistoryMessages(int maxHistoryMessages) {
        agentLoop.setMaxHistoryMessages(maxHistoryMessages);
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
        private final UUID playerId;

        McSessionContext(UUID playerId) {
            this.playerId = playerId;
        }

        @Override
        public Optional<SessionSnapshot> getSession(UUID sessionId) {
            return ChatAENetwork.SESSIONS.get(sessionId);
        }

        @Override
        public void appendMessage(UUID sessionId, ChatMessage message) {
            ChatAENetwork.appendMessageAndBroadcast(sessionId, message);
        }

        @Override
        public Optional<TerminalContext> getTerminal(AgentPlayerContext playerCtx) {
            return ChatAENetwork.findPlayer(playerId)
                    .flatMap(TerminalContextFactory::fromPlayer);
        }

        @Override
        public ToolOutcome executeTool(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
            return ToolRouter.execute(terminal, call, approved);
        }

        @Override
        public String renderPrompt(PromptId promptId, String locale, Map<String, String> variables) {
            return PromptRuntime.render(promptId, locale, variables);
        }

        @Override
        public void logDebug(String message, Object... args) {
            ChatAE.LOGGER.debug(message, args);
        }

        @Override
        public void logError(String message, Throwable error) {
            ChatAE.LOGGER.error(message, error);
        }
    }
}

package space.controlnet.mineagent.common.agent;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.common.MineAgent;
import space.controlnet.mineagent.common.audit.AuditLogger;
import space.controlnet.mineagent.core.agent.AgentLoop;
import space.controlnet.mineagent.core.agent.AgentLoopResult;
import space.controlnet.mineagent.core.agent.AgentPlayerContext;
import space.controlnet.mineagent.core.agent.AgentSessionContext;
import space.controlnet.mineagent.core.agent.Logger;
import space.controlnet.mineagent.core.session.TerminalBinding;

import java.io.Serializable;
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
                MineAgent.LOGGER.warn(message, exception);
            }

            @Override
            public void debug(String message) {
                MineAgent.LOGGER.debug(message);
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
}

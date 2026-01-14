package space.controlnet.chatae.core.agent;

import java.util.UUID;

/**
 * Context interface for agent operations that require player information.
 * This abstracts away MC-specific player types.
 */
public interface AgentPlayerContext {
    /**
     * Gets the player's unique ID.
     */
    UUID getPlayerId();

    /**
     * Gets the player's display name.
     */
    String getPlayerName();
}

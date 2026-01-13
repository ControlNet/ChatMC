package space.controlnet.chatae.core.session;

import space.controlnet.chatae.core.proposal.ApprovalDecision;

import java.util.Optional;
import java.util.UUID;

public record DecisionLogEntry(
        long timestampMillis,
        Optional<UUID> playerId,
        Optional<String> playerName,
        String proposalId,
        Optional<String> toolName,
        ApprovalDecision decision
) {
    public DecisionLogEntry {
        playerId = playerId == null ? Optional.empty() : playerId;
        playerName = playerName == null ? Optional.empty() : playerName;
        toolName = toolName == null ? Optional.empty() : toolName;
    }
}

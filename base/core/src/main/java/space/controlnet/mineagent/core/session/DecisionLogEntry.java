package space.controlnet.mineagent.core.session;

import space.controlnet.mineagent.core.proposal.ApprovalDecision;

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

package space.controlnet.mineagent.core.session;

import space.controlnet.mineagent.core.proposal.Proposal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public record SessionSnapshot(
        SessionMetadata metadata,
        List<ChatMessage> messages,
        SessionState state,
        Optional<Proposal> pendingProposal,
        Optional<TerminalBinding> proposalBinding,
        List<DecisionLogEntry> decisions,
        Optional<String> lastError
) {
    public SessionSnapshot {
        messages = List.copyOf(messages);
        pendingProposal = pendingProposal == null ? Optional.empty() : pendingProposal;
        proposalBinding = proposalBinding == null ? Optional.empty() : proposalBinding;
        decisions = List.copyOf(decisions);
        lastError = lastError == null ? Optional.empty() : lastError;
    }

    public static SessionSnapshot empty(UUID ownerId, String ownerName) {
        long now = System.currentTimeMillis();
        SessionMetadata metadata = new SessionMetadata(
                UUID.randomUUID(),
                ownerId,
                ownerName,
                SessionVisibility.PRIVATE,
                Optional.empty(),
                "New Session",
                now,
                now
        );
        return new SessionSnapshot(metadata, List.of(), SessionState.IDLE, Optional.empty(), Optional.empty(), List.of(), Optional.empty());
    }

    public static SessionSnapshot emptyClient() {
        return empty(new UUID(0L, 0L), "Unknown");
    }
}

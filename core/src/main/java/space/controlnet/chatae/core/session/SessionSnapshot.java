package space.controlnet.chatae.core.session;

import space.controlnet.chatae.core.proposal.Proposal;

import java.util.List;
import java.util.Optional;

public record SessionSnapshot(
        List<ChatMessage> messages,
        SessionState state,
        Optional<Proposal> pendingProposal,
        Optional<String> lastError
) {
    public SessionSnapshot {
        messages = List.copyOf(messages);
        pendingProposal = pendingProposal == null ? Optional.empty() : pendingProposal;
        lastError = lastError == null ? Optional.empty() : lastError;
    }

    public static SessionSnapshot empty() {
        return new SessionSnapshot(List.of(), SessionState.IDLE, Optional.empty(), Optional.empty());
    }
}

package space.controlnet.chatae.core.session;

import space.controlnet.chatae.core.proposal.Proposal;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSessionManager {
    private final ConcurrentHashMap<UUID, SessionSnapshot> sessions = new ConcurrentHashMap<>();

    public SessionSnapshot get(UUID playerId) {
        return sessions.getOrDefault(playerId, SessionSnapshot.empty());
    }

    public SessionSnapshot appendMessage(UUID playerId, ChatMessage message) {
        return update(playerId, snapshot -> {
            List<ChatMessage> messages = new ArrayList<>(snapshot.messages());
            messages.add(message);
            return new SessionSnapshot(messages, snapshot.state(), snapshot.pendingProposal(), snapshot.lastError());
        });
    }

    public SessionSnapshot setState(UUID playerId, SessionState state) {
        return update(playerId, snapshot -> new SessionSnapshot(snapshot.messages(), state, snapshot.pendingProposal(), snapshot.lastError()));
    }

    public SessionSnapshot setProposal(UUID playerId, Proposal proposal) {
        return update(playerId, snapshot -> new SessionSnapshot(snapshot.messages(), SessionState.WAIT_APPROVAL, Optional.of(proposal), snapshot.lastError()));
    }

    public SessionSnapshot clearProposal(UUID playerId) {
        return update(playerId, snapshot -> new SessionSnapshot(snapshot.messages(), SessionState.IDLE, Optional.empty(), snapshot.lastError()));
    }

    public SessionSnapshot setError(UUID playerId, String error) {
        return update(playerId, snapshot -> new SessionSnapshot(snapshot.messages(), SessionState.FAILED, snapshot.pendingProposal(), Optional.ofNullable(error)));
    }

    private SessionSnapshot update(UUID playerId, java.util.function.UnaryOperator<SessionSnapshot> fn) {
        return sessions.compute(playerId, (id, prev) -> fn.apply(prev == null ? SessionSnapshot.empty() : prev));
    }

    public void remove(UUID playerId) {
        sessions.remove(playerId);
    }
}

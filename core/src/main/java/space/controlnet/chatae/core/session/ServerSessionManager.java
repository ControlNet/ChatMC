package space.controlnet.chatae.core.session;

import space.controlnet.chatae.core.proposal.Proposal;
import space.controlnet.chatae.core.session.ChatRole;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ServerSessionManager {
    private final ConcurrentHashMap<UUID, SessionSnapshot> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> activeSessionByPlayer = new ConcurrentHashMap<>();

    public SessionSnapshot getActive(UUID playerId, String playerName) {
        UUID sessionId = activeSessionByPlayer.get(playerId);
        if (sessionId != null) {
            SessionSnapshot snapshot = sessions.get(sessionId);
            if (snapshot != null) {
                return snapshot;
            }
        }
        SessionSnapshot created = SessionSnapshot.empty(playerId, playerName);
        sessions.put(created.metadata().sessionId(), created);
        activeSessionByPlayer.put(playerId, created.metadata().sessionId());
        return created;
    }

    public SessionSnapshot create(UUID ownerId, String ownerName) {
        SessionSnapshot created = SessionSnapshot.empty(ownerId, ownerName);
        sessions.put(created.metadata().sessionId(), created);
        activeSessionByPlayer.put(ownerId, created.metadata().sessionId());
        return created;
    }

    public Optional<SessionSnapshot> get(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public List<SessionSnapshot> listAll() {
        return List.copyOf(sessions.values());
    }

    public void setActive(UUID playerId, UUID sessionId) {
        activeSessionByPlayer.put(playerId, sessionId);
    }

    public Optional<UUID> getActiveSessionId(UUID playerId) {
        return Optional.ofNullable(activeSessionByPlayer.get(playerId));
    }

    public SessionSnapshot appendMessage(UUID sessionId, ChatMessage message) {
        return update(sessionId, snapshot -> {
            List<ChatMessage> messages = new ArrayList<>(snapshot.messages());
            messages.add(message);
            SessionMetadata meta = touch(snapshot.metadata(), message);
            return new SessionSnapshot(meta, messages, snapshot.state(), snapshot.pendingProposal(), snapshot.lastError());
        });
    }

    public SessionSnapshot setState(UUID sessionId, SessionState state) {
        return update(sessionId, snapshot -> new SessionSnapshot(snapshot.metadata(), snapshot.messages(), state, snapshot.pendingProposal(), snapshot.lastError()));
    }

    public SessionSnapshot setProposal(UUID sessionId, Proposal proposal) {
        return update(sessionId, snapshot -> new SessionSnapshot(snapshot.metadata(), snapshot.messages(), SessionState.WAIT_APPROVAL, Optional.of(proposal), snapshot.lastError()));
    }

    public SessionSnapshot clearProposal(UUID sessionId) {
        return update(sessionId, snapshot -> new SessionSnapshot(snapshot.metadata(), snapshot.messages(), SessionState.IDLE, Optional.empty(), snapshot.lastError()));
    }

    public SessionSnapshot setError(UUID sessionId, String error) {
        return update(sessionId, snapshot -> new SessionSnapshot(snapshot.metadata(), snapshot.messages(), SessionState.FAILED, snapshot.pendingProposal(), Optional.ofNullable(error)));
    }

    public SessionSnapshot rename(UUID sessionId, String title) {
        return update(sessionId, snapshot -> new SessionSnapshot(renameMetadata(snapshot.metadata(), title), snapshot.messages(), snapshot.state(), snapshot.pendingProposal(), snapshot.lastError()));
    }

    public SessionSnapshot setVisibility(UUID sessionId, SessionVisibility visibility, Optional<String> teamId) {
        return update(sessionId, snapshot -> new SessionSnapshot(updateVisibility(snapshot.metadata(), visibility, teamId), snapshot.messages(), snapshot.state(), snapshot.pendingProposal(), snapshot.lastError()));
    }

    public void delete(UUID sessionId) {
        SessionSnapshot removed = sessions.remove(sessionId);
        if (removed != null) {
            UUID ownerId = removed.metadata().ownerId();
            activeSessionByPlayer.computeIfPresent(ownerId, (id, active) -> active.equals(sessionId) ? null : active);
        }
    }

    private SessionSnapshot update(UUID sessionId, java.util.function.UnaryOperator<SessionSnapshot> fn) {
        return sessions.compute(sessionId, (id, prev) -> fn.apply(prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev));
    }

    private static SessionMetadata touch(SessionMetadata metadata, ChatMessage message) {
        long now = message.timestampMillis();
        String title = metadata.title();
        if ((title == null || title.isBlank() || "New Session".equals(title)) && message.role() == ChatRole.USER) {
            title = message.text().length() > 60 ? message.text().substring(0, 60) : message.text();
        }
        return new SessionMetadata(metadata.sessionId(), metadata.ownerId(), metadata.ownerName(), metadata.visibility(), metadata.teamId(), title, metadata.createdAtMillis(), now);
    }

    private static SessionMetadata renameMetadata(SessionMetadata metadata, String title) {
        return new SessionMetadata(metadata.sessionId(), metadata.ownerId(), metadata.ownerName(), metadata.visibility(), metadata.teamId(), title, metadata.createdAtMillis(), metadata.lastActiveMillis());
    }

    private static SessionMetadata updateVisibility(SessionMetadata metadata, SessionVisibility visibility, Optional<String> teamId) {
        return new SessionMetadata(metadata.sessionId(), metadata.ownerId(), metadata.ownerName(), visibility, teamId, metadata.title(), metadata.createdAtMillis(), metadata.lastActiveMillis());
    }
}

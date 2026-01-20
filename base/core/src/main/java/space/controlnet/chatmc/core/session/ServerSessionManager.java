package space.controlnet.chatmc.core.session;

import space.controlnet.chatmc.core.proposal.Proposal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ServerSessionManager {
    private static final int MAX_MESSAGES_PER_SESSION = intProperty("chatmc.maxMessagesPerSession", 400);
    private static final int MAX_DECISIONS_PER_SESSION = intProperty("chatmc.maxDecisionsPerSession", 200);
    private static final int MAX_SESSIONS_TOTAL = intProperty("chatmc.maxSessionsTotal", 200);
    private static final int MAX_MESSAGE_LENGTH = intProperty("chatmc.maxMessageLength", 65536);

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
        evictIfNeeded();
        return created;
    }

    public SessionSnapshot create(UUID ownerId, String ownerName) {
        SessionSnapshot created = SessionSnapshot.empty(ownerId, ownerName);
        sessions.put(created.metadata().sessionId(), created);
        activeSessionByPlayer.put(ownerId, created.metadata().sessionId());
        evictIfNeeded();
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
            ChatMessage normalized = normalizeMessage(message);
            messages.add(normalized);
            if (messages.size() > MAX_MESSAGES_PER_SESSION) {
                messages.subList(0, messages.size() - MAX_MESSAGES_PER_SESSION).clear();
            }
            SessionMetadata meta = touch(snapshot.metadata(), normalized);
            return new SessionSnapshot(
                    meta,
                    messages,
                    snapshot.state(),
                    snapshot.pendingProposal(),
                    snapshot.proposalBinding(),
                    snapshot.decisions(),
                    snapshot.lastError()
            );
        });
    }

    public SessionSnapshot appendDecision(UUID sessionId, DecisionLogEntry entry) {
        return update(sessionId, snapshot -> {
            List<DecisionLogEntry> decisions = new ArrayList<>(snapshot.decisions());
            decisions.add(entry);
            if (decisions.size() > MAX_DECISIONS_PER_SESSION) {
                decisions.subList(0, decisions.size() - MAX_DECISIONS_PER_SESSION).clear();
            }
            return new SessionSnapshot(
                    snapshot.metadata(),
                    snapshot.messages(),
                    snapshot.state(),
                    snapshot.pendingProposal(),
                    snapshot.proposalBinding(),
                    decisions,
                    snapshot.lastError()
            );
        });
    }

    public SessionSnapshot setState(UUID sessionId, SessionState state) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                snapshot.metadata(),
                snapshot.messages(),
                state,
                snapshot.pendingProposal(),
                snapshot.proposalBinding(),
                snapshot.decisions(),
                snapshot.lastError()
        ));
    }

    public SessionSnapshot setProposal(UUID sessionId, Proposal proposal, TerminalBinding binding) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                snapshot.metadata(),
                snapshot.messages(),
                SessionState.WAIT_APPROVAL,
                Optional.of(proposal),
                Optional.ofNullable(binding),
                snapshot.decisions(),
                snapshot.lastError()
        ));
    }

    public SessionSnapshot clearProposal(UUID sessionId) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                snapshot.metadata(),
                snapshot.messages(),
                SessionState.IDLE,
                Optional.empty(),
                Optional.empty(),
                snapshot.decisions(),
                snapshot.lastError()
        ));
    }

    public SessionSnapshot clearProposalPreserveState(UUID sessionId) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                snapshot.metadata(),
                snapshot.messages(),
                snapshot.state(),
                Optional.empty(),
                Optional.empty(),
                snapshot.decisions(),
                snapshot.lastError()
        ));
    }

    public SessionSnapshot setError(UUID sessionId, String error) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                snapshot.metadata(),
                snapshot.messages(),
                SessionState.FAILED,
                snapshot.pendingProposal(),
                snapshot.proposalBinding(),
                snapshot.decisions(),
                Optional.ofNullable(error)
        ));
    }

    public SessionSnapshot rename(UUID sessionId, String title) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                renameMetadata(snapshot.metadata(), title),
                snapshot.messages(),
                snapshot.state(),
                snapshot.pendingProposal(),
                snapshot.proposalBinding(),
                snapshot.decisions(),
                snapshot.lastError()
        ));
    }

    public SessionSnapshot setVisibility(UUID sessionId, SessionVisibility visibility, Optional<String> teamId) {
        return update(sessionId, snapshot -> new SessionSnapshot(
                updateVisibility(snapshot.metadata(), visibility, teamId),
                snapshot.messages(),
                snapshot.state(),
                snapshot.pendingProposal(),
                snapshot.proposalBinding(),
                snapshot.decisions(),
                snapshot.lastError()
        ));
    }

    public void delete(UUID sessionId) {
        SessionSnapshot removed = sessions.remove(sessionId);
        if (removed != null) {
            UUID ownerId = removed.metadata().ownerId();
            activeSessionByPlayer.computeIfPresent(ownerId, (id, active) -> active.equals(sessionId) ? null : active);
        }
    }

    public PersistedSessions exportForSave() {
        return new PersistedSessions(1, listAll(), new HashMap<>(activeSessionByPlayer));
    }

    public void loadFromSave(PersistedSessions persisted) {
        sessions.clear();
        activeSessionByPlayer.clear();

        for (SessionSnapshot snapshot : persisted.sessions()) {
            SessionSnapshot normalized = normalizeOnLoad(snapshot);
            sessions.put(normalized.metadata().sessionId(), normalized);
        }
        activeSessionByPlayer.putAll(persisted.activeSessionByPlayer());
        evictIfNeeded();
    }

    public boolean tryStartThinking(UUID sessionId) {
        AtomicBoolean ok = new AtomicBoolean(false);
        sessions.compute(sessionId, (id, prev) -> {
            SessionSnapshot snapshot = prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev;
            if (!isIdleLike(snapshot.state()) || snapshot.pendingProposal().isPresent()) {
                return snapshot;
            }
            ok.set(true);
            return new SessionSnapshot(
                    snapshot.metadata(),
                    snapshot.messages(),
                    SessionState.THINKING,
                    snapshot.pendingProposal(),
                    snapshot.proposalBinding(),
                    snapshot.decisions(),
                    snapshot.lastError()
            );
        });
        return ok.get();
    }

    public boolean trySetProposal(UUID sessionId, Proposal proposal, TerminalBinding binding) {
        AtomicBoolean ok = new AtomicBoolean(false);
        sessions.compute(sessionId, (id, prev) -> {
            SessionSnapshot snapshot = prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev;
            if (snapshot.state() != SessionState.EXECUTING) {
                return snapshot;
            }
            ok.set(true);
            return new SessionSnapshot(
                    snapshot.metadata(),
                    snapshot.messages(),
                    SessionState.WAIT_APPROVAL,
                    Optional.ofNullable(proposal),
                    Optional.ofNullable(binding),
                    snapshot.decisions(),
                    snapshot.lastError()
            );
        });
        return ok.get();
    }

    public boolean tryStartExecuting(UUID sessionId, String proposalId) {
        AtomicBoolean ok = new AtomicBoolean(false);
        sessions.compute(sessionId, (id, prev) -> {
            SessionSnapshot snapshot = prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev;
            if (snapshot.state() != SessionState.WAIT_APPROVAL) {
                return snapshot;
            }
            Optional<Proposal> proposal = snapshot.pendingProposal();
            if (proposal.isEmpty() || !proposal.get().id().equals(proposalId)) {
                return snapshot;
            }
            ok.set(true);
            return new SessionSnapshot(
                    snapshot.metadata(),
                    snapshot.messages(),
                    SessionState.EXECUTING,
                    snapshot.pendingProposal(),
                    snapshot.proposalBinding(),
                    snapshot.decisions(),
                    snapshot.lastError()
            );
        });
        return ok.get();
    }

    public boolean tryFailProposal(UUID sessionId, String proposalId, String errorMessage) {
        AtomicBoolean ok = new AtomicBoolean(false);
        sessions.compute(sessionId, (id, prev) -> {
            SessionSnapshot snapshot = prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev;
            Optional<Proposal> proposal = snapshot.pendingProposal();
            if (proposal.isEmpty() || !proposal.get().id().equals(proposalId)) {
                return snapshot;
            }
            ok.set(true);
            return new SessionSnapshot(
                    snapshot.metadata(),
                    snapshot.messages(),
                    SessionState.FAILED,
                    Optional.empty(),
                    Optional.empty(),
                    snapshot.decisions(),
                    Optional.ofNullable(errorMessage)
            );
        });
        return ok.get();
    }

    public boolean tryResolveExecution(UUID sessionId, String proposalId, String resultMessage, SessionState finalState) {
        AtomicBoolean ok = new AtomicBoolean(false);
        sessions.compute(sessionId, (id, prev) -> {
            SessionSnapshot snapshot = prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev;
            Optional<Proposal> proposal = snapshot.pendingProposal();
            if (proposal.isEmpty() || !proposal.get().id().equals(proposalId)) {
                return snapshot;
            }

            List<ChatMessage> messages = snapshot.messages();
            if (resultMessage != null && !resultMessage.isBlank()) {
                List<ChatMessage> copy = new ArrayList<>(messages);
                copy.add(normalizeMessage(new ChatMessage(ChatRole.ASSISTANT, resultMessage, System.currentTimeMillis())));
                if (copy.size() > MAX_MESSAGES_PER_SESSION) {
                    copy.subList(0, copy.size() - MAX_MESSAGES_PER_SESSION).clear();
                }
                messages = copy;
            }

            ok.set(true);
            return new SessionSnapshot(
                    snapshot.metadata(),
                    messages,
                    finalState,
                    Optional.empty(),
                    Optional.empty(),
                    snapshot.decisions(),
                    snapshot.lastError()
            );
        });
        return ok.get();
    }

    private SessionSnapshot update(UUID sessionId, java.util.function.UnaryOperator<SessionSnapshot> fn) {
        return sessions.compute(sessionId, (id, prev) -> fn.apply(prev == null ? SessionSnapshot.empty(UUID.randomUUID(), "Unknown") : prev));
    }

    private void evictIfNeeded() {
        while (sessions.size() > MAX_SESSIONS_TOTAL) {
            SessionSnapshot oldest = sessions.values().stream()
                    .min(Comparator.comparingLong(s -> s.metadata().lastActiveMillis()))
                    .orElse(null);
            if (oldest == null) {
                return;
            }
            delete(oldest.metadata().sessionId());
        }
    }

    private static SessionSnapshot normalizeOnLoad(SessionSnapshot snapshot) {
        SessionState state = snapshot.state();
        if (state == SessionState.THINKING || state == SessionState.EXECUTING) {
            state = snapshot.pendingProposal().isPresent() ? SessionState.WAIT_APPROVAL : SessionState.IDLE;
        }

        List<ChatMessage> messages = snapshot.messages();
        if (messages.size() > MAX_MESSAGES_PER_SESSION) {
            messages = messages.subList(messages.size() - MAX_MESSAGES_PER_SESSION, messages.size());
        }
        messages = messages.stream().map(ServerSessionManager::normalizeMessage).toList();

        List<DecisionLogEntry> decisions = snapshot.decisions();
        if (decisions.size() > MAX_DECISIONS_PER_SESSION) {
            decisions = decisions.subList(decisions.size() - MAX_DECISIONS_PER_SESSION, decisions.size());
        }

        return new SessionSnapshot(
                snapshot.metadata(),
                messages,
                state,
                snapshot.pendingProposal(),
                snapshot.proposalBinding(),
                decisions,
                snapshot.lastError()
        );
    }

    private static boolean isIdleLike(SessionState state) {
        return state == SessionState.IDLE || state == SessionState.DONE || state == SessionState.FAILED;
    }

    private static ChatMessage normalizeMessage(ChatMessage message) {
        if (message == null) {
            return new ChatMessage(ChatRole.SYSTEM, "", System.currentTimeMillis());
        }
        String text = message.text();
        if (text == null) {
            text = "";
        }
        if (text.length() > MAX_MESSAGE_LENGTH) {
            text = text.substring(0, MAX_MESSAGE_LENGTH);
        }
        return new ChatMessage(message.role(), text, message.timestampMillis());
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

    private static int intProperty(String key, int def) {
        String raw = System.getProperty(key);
        if (raw == null || raw.isBlank()) {
            return def;
        }
        try {
            int value = Integer.parseInt(raw);
            return value <= 0 ? def : value;
        } catch (NumberFormatException ignored) {
            return def;
        }
    }
}

package space.controlnet.chatmc.core.session;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates session state transitions and viewer management.
 * This class contains the core session workflow logic without MC dependencies.
 */
public final class SessionOrchestrator {
    private final ServerSessionManager sessions;
    private final ConcurrentHashMap<UUID, Set<UUID>> viewersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, UUID> sessionByViewer = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, String> sessionLocale = new ConcurrentHashMap<>();

    /**
     * Callback interface for session events.
     */
    public interface SessionEventHandler {
        /**
         * Called when a session snapshot should be broadcast to viewers.
         */
        void broadcastSnapshot(UUID sessionId, SessionSnapshot snapshot, Set<UUID> viewerIds);

        /**
         * Called to check if a viewer can view a session.
         */
        boolean canView(UUID viewerId, SessionSnapshot snapshot);

        /**
         * Called to check if the recipe index is ready.
         */
        boolean isRecipeIndexReady();

        /**
         * Called when a viewer should be notified of session deletion.
         */
        void onViewerSessionDeleted(UUID viewerId);
    }

    public SessionOrchestrator(ServerSessionManager sessions) {
        this.sessions = sessions;
    }

    /**
     * Gets the underlying session manager.
     */
    public ServerSessionManager getSessions() {
        return sessions;
    }

    /**
     * Subscribes a viewer to a session.
     */
    public void subscribeViewer(UUID viewerId, UUID sessionId) {
        UUID previous = sessionByViewer.put(viewerId, sessionId);
        if (previous != null && !previous.equals(sessionId)) {
            Set<UUID> prevSet = viewersBySession.get(previous);
            if (prevSet != null) {
                prevSet.remove(viewerId);
                if (prevSet.isEmpty()) {
                    viewersBySession.remove(previous, prevSet);
                }
            }
        }
        viewersBySession.computeIfAbsent(sessionId, ignored -> ConcurrentHashMap.newKeySet()).add(viewerId);
    }

    /**
     * Unsubscribes a viewer from their current session.
     */
    public void unsubscribeViewer(UUID viewerId) {
        UUID sessionId = sessionByViewer.remove(viewerId);
        if (sessionId == null) {
            return;
        }
        Set<UUID> set = viewersBySession.get(sessionId);
        if (set != null) {
            set.remove(viewerId);
            if (set.isEmpty()) {
                viewersBySession.remove(sessionId, set);
            }
        }
    }

    /**
     * Gets the viewers for a session.
     */
    public Set<UUID> getViewers(UUID sessionId) {
        Set<UUID> viewers = viewersBySession.get(sessionId);
        return viewers != null ? Set.copyOf(viewers) : Set.of();
    }

    /**
     * Gets the session a viewer is subscribed to.
     */
    public Optional<UUID> getViewerSession(UUID viewerId) {
        return Optional.ofNullable(sessionByViewer.get(viewerId));
    }

    /**
     * Stores the locale for a session (for approval resume).
     */
    public void setSessionLocale(UUID sessionId, String locale) {
        sessionLocale.put(sessionId, locale);
    }

    /**
     * Gets the stored locale for a session.
     */
    public String getSessionLocale(UUID sessionId) {
        return sessionLocale.getOrDefault(sessionId, "en_us");
    }

    /**
     * Clears the stored locale for a session.
     */
    public void clearSessionLocale(UUID sessionId) {
        sessionLocale.remove(sessionId);
    }

    /**
     * Broadcasts a session snapshot to all viewers.
     */
    public void broadcastSessionSnapshot(UUID sessionId, SessionEventHandler handler) {
        Set<UUID> viewers = viewersBySession.get(sessionId);
        if (viewers == null || viewers.isEmpty()) {
            return;
        }

        SessionSnapshot base = sessions.get(sessionId).orElse(null);
        if (base == null) {
            // Session deleted, unsubscribe all viewers
            for (UUID viewerId : List.copyOf(viewers)) {
                unsubscribeViewer(viewerId);
            }
            return;
        }

        // Filter viewers who can still view the session
        Set<UUID> validViewers = ConcurrentHashMap.newKeySet();
        for (UUID viewerId : List.copyOf(viewers)) {
            if (handler.canView(viewerId, base)) {
                validViewers.add(viewerId);
            } else {
                unsubscribeViewer(viewerId);
            }
        }

        if (!validViewers.isEmpty()) {
            SessionSnapshot snapshot = ensureIndexingStateIfNeeded(base, handler);
            handler.broadcastSnapshot(sessionId, snapshot, validViewers);
        }
    }

    /**
     * Ensures the session state reflects indexing status if needed.
     */
    public SessionSnapshot ensureIndexingStateIfNeeded(SessionSnapshot snapshot, SessionEventHandler handler) {
        if (!handler.isRecipeIndexReady()
                && (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.DONE)) {
            return sessions.setState(snapshot.metadata().sessionId(), SessionState.INDEXING);
        }
        return snapshot;
    }

    /**
     * Handles terminal opened event.
     * Returns the session snapshot to send to the player.
     */
    public SessionSnapshot onTerminalOpened(UUID playerId, String playerName, SessionEventHandler handler) {
        SessionSnapshot snapshot = sessions.getActive(playerId, playerName);
        if (!handler.canView(playerId, snapshot)) {
            snapshot = sessions.create(playerId, playerName);
            sessions.setActive(playerId, snapshot.metadata().sessionId());
        }
        subscribeViewer(playerId, snapshot.metadata().sessionId());
        return snapshot;
    }

    /**
     * Handles terminal closed event.
     */
    public void onTerminalClosed(UUID playerId) {
        unsubscribeViewer(playerId);
    }

    /**
     * Handles session deletion.
     */
    public void onSessionDeleted(UUID sessionId, SessionEventHandler handler) {
        Set<UUID> viewers = viewersBySession.remove(sessionId);
        if (viewers != null) {
            for (UUID viewerId : List.copyOf(viewers)) {
                sessionByViewer.remove(viewerId, sessionId);
                handler.onViewerSessionDeleted(viewerId);
            }
        }
    }

    /**
     * Clears all viewer subscriptions (e.g., on server shutdown).
     */
    public void clearAllViewers() {
        viewersBySession.clear();
        sessionByViewer.clear();
        sessionLocale.clear();
    }
}

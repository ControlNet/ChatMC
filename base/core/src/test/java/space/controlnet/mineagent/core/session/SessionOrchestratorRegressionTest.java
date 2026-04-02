package space.controlnet.mineagent.core.session;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class SessionOrchestratorRegressionTest {
    private static final UUID OWNER_ID = UUID.fromString("00000000-0000-0000-0000-000000000411");
    private static final UUID VIEWER_A = UUID.fromString("00000000-0000-0000-0000-000000000412");
    private static final UUID VIEWER_B = UUID.fromString("00000000-0000-0000-0000-000000000413");
    private static final UUID VIEWER_C = UUID.fromString("00000000-0000-0000-0000-000000000414");

    @Test
    void task14_subscribeViewer_reassignsBetweenSessions_andCleansOldSet() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);

        UUID firstSession = manager.create(OWNER_ID, "owner-task14-a").metadata().sessionId();
        UUID secondSession = manager.create(OWNER_ID, "owner-task14-b").metadata().sessionId();

        orchestrator.subscribeViewer(VIEWER_A, firstSession);
        assertEquals("task14/subscriber-in-first-session", Set.of(VIEWER_A), orchestrator.getViewers(firstSession));

        orchestrator.subscribeViewer(VIEWER_A, secondSession);
        assertEquals("task14/old-session-cleaned", Set.of(), orchestrator.getViewers(firstSession));
        assertEquals("task14/new-session-receives-viewer", Set.of(VIEWER_A), orchestrator.getViewers(secondSession));
        assertEquals("task14/viewer-session-updated", Optional.of(secondSession), orchestrator.getViewerSession(VIEWER_A));
    }

    @Test
    void task14_unsubscribeViewer_removesMappingAndEmptiesSessionSet() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);
        UUID sessionId = manager.create(OWNER_ID, "owner-task14").metadata().sessionId();

        orchestrator.subscribeViewer(VIEWER_A, sessionId);
        orchestrator.unsubscribeViewer(VIEWER_A);

        assertEquals("task14/unsubscribe-clears-viewer-session", Optional.empty(), orchestrator.getViewerSession(VIEWER_A));
        assertEquals("task14/unsubscribe-clears-session-viewers", Set.of(), orchestrator.getViewers(sessionId));
    }

    @Test
    void task14_broadcastSessionSnapshot_filtersViewers_andBroadcastsIndexedState() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);
        UUID sessionId = manager.create(OWNER_ID, "owner-task14").metadata().sessionId();

        orchestrator.subscribeViewer(VIEWER_A, sessionId);
        orchestrator.subscribeViewer(VIEWER_B, sessionId);

        RecordingHandler handler = new RecordingHandler();
        handler.allowedViewers.add(VIEWER_A);
        handler.recipeIndexReady = false;

        orchestrator.broadcastSessionSnapshot(sessionId, handler);

        assertEquals("task14/broadcast-called-once", 1, handler.broadcastCalls);
        assertEquals("task14/broadcast-only-valid-viewers", Set.of(VIEWER_A), handler.lastViewerIds);
        assertEquals("task14/disallowed-viewer-unsubscribed", Optional.empty(), orchestrator.getViewerSession(VIEWER_B));
        assertEquals("task14/session-moved-to-indexing", SessionState.INDEXING, handler.lastSnapshot.state());
    }

    @Test
    void task14_broadcastSessionSnapshot_missingSession_unsubscribesAllViewers() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);
        UUID missingSession = UUID.fromString("00000000-0000-0000-0000-000000001499");

        orchestrator.subscribeViewer(VIEWER_A, missingSession);
        orchestrator.subscribeViewer(VIEWER_B, missingSession);

        RecordingHandler handler = new RecordingHandler();
        orchestrator.broadcastSessionSnapshot(missingSession, handler);

        assertEquals("task14/no-broadcast-when-missing-session", 0, handler.broadcastCalls);
        assertEquals("task14/missing-session-clears-viewer-a", Optional.empty(), orchestrator.getViewerSession(VIEWER_A));
        assertEquals("task14/missing-session-clears-viewer-b", Optional.empty(), orchestrator.getViewerSession(VIEWER_B));
        assertEquals("task14/missing-session-viewers-empty", Set.of(), orchestrator.getViewers(missingSession));
    }

    @Test
    void task14_onTerminalOpened_whenCannotView_createsNewSessionAndResubscribes() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);

        SessionSnapshot original = manager.getActive(OWNER_ID, "owner-task14");
        RecordingHandler handler = new RecordingHandler();
        handler.allowAll = false;

        SessionSnapshot opened = orchestrator.onTerminalOpened(OWNER_ID, "owner-task14", handler);

        assertTrue("task14/new-session-created", !opened.metadata().sessionId().equals(original.metadata().sessionId()));
        assertEquals("task14/new-session-set-active", Optional.of(opened.metadata().sessionId()), manager.getActiveSessionId(OWNER_ID));
        assertEquals("task14/viewer-subscribed-to-new-session",
                Optional.of(opened.metadata().sessionId()),
                orchestrator.getViewerSession(OWNER_ID));
    }

    @Test
    void task14_sessionLocale_roundTripAndDefault_areStable() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);
        UUID sessionId = manager.create(OWNER_ID, "owner-task14").metadata().sessionId();

        assertEquals("task14/default-locale", "en_us", orchestrator.getSessionLocale(sessionId));

        orchestrator.setSessionLocale(sessionId, "de_de");
        assertEquals("task14/stored-locale", "de_de", orchestrator.getSessionLocale(sessionId));

        orchestrator.clearSessionLocale(sessionId);
        assertEquals("task14/cleared-locale-falls-back", "en_us", orchestrator.getSessionLocale(sessionId));
    }

    @Test
    void task14_onSessionDeleted_notifiesViewers_andClearsMappings() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);
        UUID sessionId = manager.create(OWNER_ID, "owner-task14").metadata().sessionId();

        orchestrator.subscribeViewer(VIEWER_A, sessionId);
        orchestrator.subscribeViewer(VIEWER_B, sessionId);

        RecordingHandler handler = new RecordingHandler();
        orchestrator.onSessionDeleted(sessionId, handler);

        assertEquals("task14/deleted-notifies-both", Set.of(VIEWER_A, VIEWER_B), handler.deletedViewers);
        assertEquals("task14/deleted-clears-viewer-a-mapping", Optional.empty(), orchestrator.getViewerSession(VIEWER_A));
        assertEquals("task14/deleted-clears-viewer-b-mapping", Optional.empty(), orchestrator.getViewerSession(VIEWER_B));
        assertEquals("task14/deleted-clears-session-viewers", Set.of(), orchestrator.getViewers(sessionId));
    }

    @Test
    void task14_clearAllViewers_clearsSubscriptionsAndLocales() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionOrchestrator orchestrator = new SessionOrchestrator(manager);
        UUID sessionId = manager.create(OWNER_ID, "owner-task14").metadata().sessionId();

        orchestrator.subscribeViewer(VIEWER_A, sessionId);
        orchestrator.subscribeViewer(VIEWER_C, sessionId);
        orchestrator.setSessionLocale(sessionId, "fr_fr");

        orchestrator.clearAllViewers();

        assertEquals("task14/clear-all-viewer-a", Optional.empty(), orchestrator.getViewerSession(VIEWER_A));
        assertEquals("task14/clear-all-viewer-c", Optional.empty(), orchestrator.getViewerSession(VIEWER_C));
        assertEquals("task14/clear-all-session-viewers", Set.of(), orchestrator.getViewers(sessionId));
        assertEquals("task14/clear-all-resets-locale", "en_us", orchestrator.getSessionLocale(sessionId));
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (!value) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class RecordingHandler implements SessionOrchestrator.SessionEventHandler {
        private final Set<UUID> allowedViewers = new HashSet<>();
        private final Set<UUID> deletedViewers = new HashSet<>();
        private boolean recipeIndexReady = true;
        private boolean allowAll = true;
        private int broadcastCalls;
        private SessionSnapshot lastSnapshot;
        private Set<UUID> lastViewerIds = Set.of();

        @Override
        public void broadcastSnapshot(UUID sessionId, SessionSnapshot snapshot, Set<UUID> viewerIds) {
            this.broadcastCalls += 1;
            this.lastSnapshot = snapshot;
            this.lastViewerIds = Set.copyOf(viewerIds);
        }

        @Override
        public boolean canView(UUID viewerId, SessionSnapshot snapshot) {
            if (!allowAll) {
                return false;
            }
            return allowedViewers.isEmpty() || allowedViewers.contains(viewerId);
        }

        @Override
        public boolean isRecipeIndexReady() {
            return recipeIndexReady;
        }

        @Override
        public void onViewerSessionDeleted(UUID viewerId) {
            deletedViewers.add(viewerId);
        }
    }
}

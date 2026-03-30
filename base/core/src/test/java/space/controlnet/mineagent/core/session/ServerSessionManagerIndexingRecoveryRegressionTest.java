package space.controlnet.mineagent.core.session;

import org.junit.jupiter.api.Test;

import java.util.UUID;

public final class ServerSessionManagerIndexingRecoveryRegressionTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000313");
    private static final String PLAYER_NAME = "task13-player";

    @Test
    void task13_indexingEntry_whenIndexNotReady_transitionsIdleAndDoneToIndexing() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        SessionSnapshot idleSnapshot = requireSnapshot("task13/indexing-entry/idle-base", manager, sessionId);
        SessionSnapshot indexingFromIdle = applyTask8IndexingGate(manager, idleSnapshot, false);
        assertEquals("task13/indexing-entry/idle-transitions-to-indexing", SessionState.INDEXING, indexingFromIdle.state());

        manager.setState(sessionId, SessionState.DONE);
        SessionSnapshot doneSnapshot = requireSnapshot("task13/indexing-entry/done-base", manager, sessionId);
        SessionSnapshot indexingFromDone = applyTask8IndexingGate(manager, doneSnapshot, false);
        assertEquals("task13/indexing-entry/done-transitions-to-indexing", SessionState.INDEXING, indexingFromDone.state());
    }

    @Test
    void task13_recovery_whenReadyFlipsTrue_transitionsIndexingToIdle() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        manager.setState(sessionId, SessionState.INDEXING);
        SessionSnapshot indexingSnapshot = requireSnapshot("task13/recovery/indexing-base", manager, sessionId);

        SessionSnapshot recovered = applyTask8IndexingGate(manager, indexingSnapshot, true);
        assertEquals("task13/recovery/index-ready-recovers-to-idle", SessionState.IDLE, recovered.state());
        assertEquals("task13/recovery/manager-state-persists-idle", SessionState.IDLE,
                requireSnapshot("task13/recovery/manager-idle", manager, sessionId).state());
    }

    @Test
    void task13_persistenceReload_afterRecovery_doesNotRemainStuckInIndexing() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        manager.setState(sessionId, SessionState.INDEXING);
        PersistedSessions persistedBeforeRecovery = manager.exportForSave();

        ServerSessionManager reloadedManager = new ServerSessionManager();
        reloadedManager.loadFromSave(persistedBeforeRecovery);

        SessionSnapshot loadedIndexing = requireSnapshot(
                "task13/persistence-reload/indexing-loaded", reloadedManager, sessionId);
        assertEquals("task13/persistence-reload/indexing-state-loaded", SessionState.INDEXING, loadedIndexing.state());

        SessionSnapshot recovered = applyTask8IndexingGate(reloadedManager, loadedIndexing, true);
        assertEquals("task13/persistence-reload/recovered-to-idle", SessionState.IDLE, recovered.state());

        PersistedSessions persistedAfterRecovery = reloadedManager.exportForSave();
        ServerSessionManager secondReloadManager = new ServerSessionManager();
        secondReloadManager.loadFromSave(persistedAfterRecovery);

        SessionSnapshot loadedAfterRecovery = requireSnapshot(
                "task13/persistence-reload/loaded-after-recovery", secondReloadManager, sessionId);
        assertEquals("task13/persistence-reload/recovered-state-survives-reload", SessionState.IDLE,
                loadedAfterRecovery.state());
    }

    @Test
    void task13_requestGate_afterRecovery_allowsThinkingAgain() {
        SessionContext context = newSessionContext();
        ServerSessionManager manager = context.manager();
        UUID sessionId = context.sessionId();

        manager.setState(sessionId, SessionState.INDEXING);
        assertFalse("task13/request-gate/indexing-rejects-start-thinking", manager.tryStartThinking(sessionId));
        assertEquals("task13/request-gate/still-indexing-before-recovery", SessionState.INDEXING,
                requireSnapshot("task13/request-gate/indexing-before-recovery", manager, sessionId).state());

        SessionSnapshot recovered = applyTask8IndexingGate(
                manager,
                requireSnapshot("task13/request-gate/base-for-recovery", manager, sessionId),
                true
        );
        assertEquals("task13/request-gate/recovered-to-idle", SessionState.IDLE, recovered.state());

        assertTrue("task13/request-gate/recovery-allows-start-thinking", manager.tryStartThinking(sessionId));
        assertEquals("task13/request-gate/state-advances-to-thinking", SessionState.THINKING,
                requireSnapshot("task13/request-gate/final-thinking", manager, sessionId).state());
    }

    private static SessionSnapshot applyTask8IndexingGate(
            ServerSessionManager manager,
            SessionSnapshot snapshot,
            boolean recipeIndexReady
    ) {
        if (!recipeIndexReady) {
            if (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.DONE) {
                return manager.setState(snapshot.metadata().sessionId(), SessionState.INDEXING);
            }
            return snapshot;
        }

        if (snapshot.state() == SessionState.INDEXING) {
            return manager.setState(snapshot.metadata().sessionId(), SessionState.IDLE);
        }

        return snapshot;
    }

    private SessionContext newSessionContext() {
        ServerSessionManager manager = new ServerSessionManager();
        SessionSnapshot snapshot = manager.create(PLAYER_ID, PLAYER_NAME);
        return new SessionContext(manager, snapshot.metadata().sessionId());
    }

    private static SessionSnapshot requireSnapshot(String assertionName, ServerSessionManager manager, UUID sessionId) {
        return manager.get(sessionId).orElseThrow(() -> new AssertionError(assertionName + " -> missing session"));
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (!value) {
            throw new AssertionError(assertionName + " -> expected true");
        }
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (value) {
            throw new AssertionError(assertionName + " -> expected false");
        }
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private record SessionContext(ServerSessionManager manager, UUID sessionId) {
    }
}

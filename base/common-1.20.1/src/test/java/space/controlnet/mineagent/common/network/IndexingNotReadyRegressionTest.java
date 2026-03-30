package space.controlnet.mineagent.common.network;

import org.junit.jupiter.api.Test;

import space.controlnet.mineagent.common.testing.DeterministicBarrier;
import space.controlnet.mineagent.common.testing.TimeoutUtility;

import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class IndexingNotReadyRegressionTest {
    @Test
    void task8_IndexingNotReady_movesIdleAndDoneIntoIndexingAndPersists() {
        String source = readSource("base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/MineAgentNetwork.java");

        assertContains("task8/indexing-not-ready/readiness-guard", source,
                "if (!MineAgent.RECIPE_INDEX.isReady()) {");
        assertContains("task8/indexing-not-ready/idle-done-branch", source,
                "if (snapshot.state() == SessionState.IDLE || snapshot.state() == SessionState.DONE) {");
        assertContains("task8/indexing-not-ready/sets-indexing", source,
                "SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.INDEXING);");
        assertContains("task8/indexing-not-ready/persists-after-indexing", source,
                "persistSessions();");

        assertContains("task8/indexing-not-ready/recovery-when-ready", source,
                "if (snapshot.state() == SessionState.INDEXING) {");
        assertContains("task8/indexing-not-ready/recovery-to-idle", source,
                "SESSIONS.setState(snapshot.metadata().sessionId(), SessionState.IDLE);");
    }

    @Test
    void task8_IndexingNotReady_recoveryFlowCanBeDeterministicallyOrchestrated() {
        DeterministicBarrier indexingBarrier = new DeterministicBarrier("task8/indexing-not-ready/rebuild-barrier");
        AtomicReference<String> state = new AtomicReference<>("INDEXING");

        Thread rebuildThread = new Thread(() -> {
            indexingBarrier.arriveAndAwaitRelease("index-rebuild", Duration.ofSeconds(1));
            state.set("IDLE");
        }, "task8-indexing-rebuild");

        rebuildThread.start();
        indexingBarrier.awaitArrivals(1, Duration.ofSeconds(1));
        TimeoutUtility.retry(
                "task8/indexing-not-ready/pre-release-state",
                4,
                Duration.ofMillis(10),
                () -> "INDEXING".equals(state.get())
        );

        indexingBarrier.release();
        TimeoutUtility.awaitThreadCompletion(
                "task8/indexing-not-ready/rebuild-thread",
                rebuildThread,
                Duration.ofSeconds(1)
        );
        TimeoutUtility.await(
                "task8/indexing-not-ready/post-release-state",
                Duration.ofSeconds(1),
                () -> "IDLE".equals(state.get())
        );
    }

    private static String readSource(String path) {
        try {
            Path direct = Path.of(path);
            if (Files.exists(direct)) {
                return Files.readString(direct);
            }

            Path fromModule = Path.of("..").resolve("..").resolve(path).normalize();
            if (Files.exists(fromModule)) {
                return Files.readString(fromModule);
            }

            throw new AssertionError("read-source missing: " + path + " (checked " + direct + " and " + fromModule + ")");
        } catch (Exception exception) {
            throw new AssertionError("read-source failed: " + path, exception);
        }
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        try {
            TimeoutUtility.retry(assertionName, 3, Duration.ofMillis(5), () -> haystack.contains(needle));
        } catch (AssertionError assertionError) {
            throw new AssertionError(assertionName + " -> expected to find: " + needle, assertionError);
        }
    }
}

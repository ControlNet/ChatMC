package space.controlnet.chatmc.common.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class IndexingNotReadyRegressionTest {
    @Test
    void task8_IndexingNotReady_movesIdleAndDoneIntoIndexingAndPersists() {
        String source = readSource("base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java");

        assertContains("task8/indexing-not-ready/readiness-guard", source,
                "if (!ChatMC.RECIPE_INDEX.isReady()) {");
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
        if (!haystack.contains(needle)) {
            throw new AssertionError(assertionName + " -> expected to find: " + needle);
        }
    }
}

package space.controlnet.chatmc.common.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class IndexingGateRegressionTest {
    @Test
    void task2_IndexingGate_isAppliedAtSnapshotAndChatEntrypoints() {
        String source = readSource("base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java");

        assertContains("task2/indexing-gate/helper-exists", source,
                "private static SessionSnapshot ensureIndexingStateIfNeeded(SessionSnapshot snapshot)");
        assertContains("task2/indexing-gate/broadcast-path", source,
                "SessionSnapshot snapshot = ensureIndexingStateIfNeeded(base);");
        assertContains("task2/indexing-gate/snapshot-path", source,
                "snapshot = ensureIndexingStateIfNeeded(snapshot);");
        assertContains("task2/indexing-gate/chat-path", source,
                "snapshot = ensureIndexingStateIfNeeded(snapshot);");
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

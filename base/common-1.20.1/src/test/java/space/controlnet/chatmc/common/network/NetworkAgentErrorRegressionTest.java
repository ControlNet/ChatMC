package space.controlnet.chatmc.common.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkAgentErrorRegressionTest {
    @Test
    void task7_NetworkAgentError_emitsDeterministicMessagesAndStatePersistence() {
        String source = readSource("base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java");

        assertContains("task7/network-agent-error/chat-completion-path", source,
                "applyAgentError(sessionId, \"Agent error: \" + error.getMessage());");
        assertContains("task7/network-agent-error/null-result-path", source,
                "applyAgentError(sessionId, \"Agent returned null result\");");
        assertContains("task7/network-agent-error/invalid-proposal-path", source,
                "applyAgentError(sessionId, \"Agent returned invalid proposal result\");");
        assertContains("task7/network-agent-error/agent-loop-error-path", source,
                "applyAgentError(sessionId, result.error().get());");

        assertContains("task7/network-agent-error/error-message-prefix", source,
                "SESSIONS.appendMessage(sessionId, new ChatMessage(ChatRole.ASSISTANT, \"Error: \" + message, System.currentTimeMillis()));");
        assertContains("task7/network-agent-error/error-state-recorded", source,
                "SESSIONS.setError(sessionId, message);");
        assertContains("task7/network-agent-error/error-persisted", source,
                "persistSessions();");
        assertContains("task7/network-agent-error/error-broadcast", source,
                "broadcastSessionSnapshot(sessionId);");
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

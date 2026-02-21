package space.controlnet.chatmc.common.network;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class NetworkProposalLifecycleRegressionTest {
    @Test
    void task7_NetworkProposalLifecycle_preservesProposalApprovalResumeContract() {
        String source = readSource("base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java");

        assertContains("task7/network-proposal/loop-has-proposal-branch", source,
                "if (result.hasProposal()) {");
        assertContains("task7/network-proposal/rejected-transition-errors", source,
                "if (!SESSIONS.trySetProposal(sessionId, proposal, binding)) {");
        assertContains("task7/network-proposal/rejected-transition-message", source,
                "applyAgentError(sessionId, \"Session transition rejected proposal result\");");

        assertContains("task7/network-proposal/approval-starts-executing", source,
                "if (!SESSIONS.tryStartExecuting(sessionId, proposal.id())) {");
        assertContains("task7/network-proposal/clear-preserve-state", source,
                "SESSIONS.clearProposalPreserveState(sessionId);");
        assertContains("task7/network-proposal/resume-agent-loop", source,
                "runAgentLoopAsync(player, sessionId, binding.orElse(null), effectiveLocale)");
        assertContains("task7/network-proposal/resume-result-handler", source,
                "handleAgentLoopResult(player, result, sessionId, binding.orElse(null), effectiveLocale);");

        assertContains("task7/network-proposal/response-to-done", source,
                "SESSIONS.setState(sessionId, SessionState.DONE);");
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

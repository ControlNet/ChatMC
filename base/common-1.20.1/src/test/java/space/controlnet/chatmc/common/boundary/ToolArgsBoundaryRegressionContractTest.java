package space.controlnet.chatmc.common.boundary;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ToolArgsBoundaryRegressionContractTest {
    @Test
    void task15_networkBoundary_contractEnforces65536AndSignalMapping() {
        String source = readSource(
                "base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java"
        );

        assertContains("task15/network/max-constant", source,
                "private static final int MAX_TOOL_ARGS_JSON_LENGTH = 65_536;");
        assertContains("task15/network/signal-constant", source,
                "private static final String NETWORK_BOUNDARY_SIGNAL = \"NETWORK_BOUNDARY_TOOL_ARGS_TOO_LARGE\";");

        assertContains("task15/network/strict-greater-than", source,
                "if (length > MAX_TOOL_ARGS_JSON_LENGTH) {");
        assertContains("task15/network/error-message-prefix", source,
                "NETWORK_BOUNDARY_SIGNAL");
        assertContains("task15/network/error-message-phase-tool-length", source,
                "+ \": phase='\" + phase + \"', tool='\" + toolName + \"', argsJson.length=\" + length");
        assertContains("task15/network/error-message-max", source,
                "+ \", max=\" + MAX_TOOL_ARGS_JSON_LENGTH");

        assertContains("task15/network/encode-boundary-check", source,
                "validateToolArgsBoundary(toolCall.toolName(), toolCall.argsJson(), \"encode\");");
        assertContains("task15/network/decode-boundary-check", source,
                "validateToolArgsBoundary(toolName, argsJson, \"decode\");");
    }

    @Test
    void task15_persistenceBoundary_contractEnforces65536AndSignalMapping() {
        String source = readSource(
                "base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java"
        );

        assertContains("task15/persist/max-constant", source,
                "private static final int MAX_TOOL_ARGS_JSON_LENGTH = 65_536;");
        assertContains("task15/persist/signal-constant", source,
                "private static final String PERSIST_BOUNDARY_SIGNAL = \"PERSIST_BOUNDARY_TOOL_ARGS_TOO_LARGE\";");

        assertContains("task15/persist/strict-greater-than", source,
                "if (length > MAX_TOOL_ARGS_JSON_LENGTH) {");
        assertContains("task15/persist/error-message-prefix", source,
                "PERSIST_BOUNDARY_SIGNAL");
        assertContains("task15/persist/error-message-phase-tool-length", source,
                "+ \": phase='\" + phase + \"', tool='\" + toolName + \"', argsJson.length=\" + length");
        assertContains("task15/persist/error-message-max", source,
                "+ \", max=\" + MAX_TOOL_ARGS_JSON_LENGTH");

        assertContains("task15/persist/write-boundary-check", source,
                "validateToolArgsBoundary(call.toolName(), call.argsJson(), \"write\");");
        assertContains("task15/persist/read-boundary-check", source,
                "validateToolArgsBoundary(toolName, argsJson, \"read\");");
    }

    @Test
    void task15_boundaryContracts_useStringLengthSemanticsForUtfEdges() {
        String network = readSource(
                "base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/ChatMCNetwork.java"
        );
        String persistence = readSource(
                "base/common-1.20.1/src/main/java/space/controlnet/chatmc/common/session/ChatMCSessionsSavedData.java"
        );

        assertContains("task15/network/utf-length-semantics", network, "int length = argsJson.length();");
        assertContains("task15/persist/utf-length-semantics", persistence, "int length = argsJson.length();");
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

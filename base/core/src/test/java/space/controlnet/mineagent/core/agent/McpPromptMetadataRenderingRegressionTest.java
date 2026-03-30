package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class McpPromptMetadataRenderingRegressionTest {
    @Test
    void task17_emptyMetadataSections_areOmitted() {
        String agentLoopSource = readSource("base/core/src/main/java/space/controlnet/mineagent/core/agent/AgentLoop.java");
        String agentToolSource = readSource("base/core/src/main/java/space/controlnet/mineagent/core/tools/AgentTool.java");

        assertContains(
                "task17/mcp-prompt/agent-loop/args-detail-uses-optional-helper",
                agentLoopSource,
                "buildSection(\"Arguments Details:\", tool.argsDescriptionOptional())"
        );
        assertContains(
                "task17/mcp-prompt/agent-loop/return-detail-uses-optional-helper",
                agentLoopSource,
                "List<String> details = tool.resultDescriptionOptional();"
        );
        assertContains(
                "task17/mcp-prompt/agent-loop/examples-uses-optional-helper",
                agentLoopSource,
                "buildSection(\"Examples:\", tool.examplesOptional())"
        );
        assertContains(
                "task17/mcp-prompt/agent-loop/args-schema-skips-blank-lines",
                agentLoopSource,
                ".map(AgentLoop::buildArgsSchemaLine)"
        );
        assertContains(
                "task17/mcp-prompt/agent-loop/description-conditional",
                agentLoopSource,
                "tool.descriptionOptional().ifPresent(description -> sections.add(\"Description:\\n\" + description));"
        );
        assertContains(
                "task17/mcp-prompt/agent-loop/args-schema-conditional",
                agentLoopSource,
                "tool.argsSchemaOptional().ifPresent(schema -> sections.add(\"Arguments Schema:\\n\" + schema));"
        );

        assertNotContains(
                "task17/mcp-prompt/agent-loop/no-args-none-sentinel",
                agentLoopSource,
                "Arguments Details:\\n  - (none)"
        );
        assertNotContains(
                "task17/mcp-prompt/agent-loop/no-return-none-sentinel",
                agentLoopSource,
                "Return Details:\\n  - (none)"
        );
        assertNotContains(
                "task17/mcp-prompt/agent-loop/no-examples-none-sentinel",
                agentLoopSource,
                "Examples:\\n  - (none)"
        );

        assertContains(
                "task17/mcp-prompt/agent-tool/optional-description-helper",
                agentToolSource,
                "default Optional<String> descriptionOptional()"
        );
        assertContains(
                "task17/mcp-prompt/agent-tool/optional-result-schema-helper",
                agentToolSource,
                "default Optional<String> resultSchemaOptional()"
        );
        assertContains(
                "task17/mcp-prompt/agent-tool/optional-examples-helper",
                agentToolSource,
                "default List<String> examplesOptional()"
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
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle);
    }

    private static void assertNotContains(String assertionName, String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> did not expect to find: " + needle);
    }
}

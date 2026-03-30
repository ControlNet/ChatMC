package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.AgentToolSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class McpPromptToolOrderRegressionTest {
    @Test
    void task9e2e_registrySortedNamespacedSnapshot_feedsDeterministicAgentLoopPromptOrder() {
        String agentLoopSource = readSource("base/core/src/main/java/space/controlnet/mineagent/core/agent/AgentLoop.java");
        String toolRegistrySource = readSource("base/common-1.20.1/src/main/java/space/controlnet/mineagent/common/tools/ToolRegistry.java");

        assertContains(
                "task9e2e/prompt-order/tool-registry-sorts-by-qualified-name",
                toolRegistrySource,
                ".sorted(Comparator.comparing(AgentTool::name))"
        );
        assertContains(
                "task9e2e/prompt-order/agent-loop-tool-list-preserves-input-order",
                agentLoopSource,
                "String toolList = tools.stream()"
        );
        assertContains(
                "task9e2e/prompt-order/agent-loop-args-schema-preserves-input-order",
                agentLoopSource,
                "String argsSchema = tools.stream()"
        );
        assertContains(
                "task9e2e/prompt-order/agent-loop-tools-section-preserves-input-order",
                agentLoopSource,
                "String toolsSection = tools.stream()"
        );
        assertContains(
                "task9e2e/prompt-order/agent-loop-tool-header-uses-qualified-name",
                agentLoopSource,
                "sections.add(\"### \" + tool.name());"
        );

        List<AgentTool> refreshedSnapshot = List.of(
                        metadataOnly(
                                "mcp.task9e2e-zeta.search",
                                "",
                                "",
                                List.of(),
                                "",
                                List.of(),
                                List.of()
                        ),
                        metadataOnly(
                                "mcp.task9e2e-alpha.search",
                                "Alpha description",
                                "",
                                List.of(),
                                "",
                                List.of(),
                                List.of()
                        ),
                        metadataOnly(
                                "mcp.task9e2e-beta.fetch_page",
                                "",
                                "{\n  \"type\": \"object\"\n}",
                                List.of("path: required string - Documentation path"),
                                "",
                                List.of(),
                                List.of()
                        ))
                .stream()
                .sorted(Comparator.comparing(AgentTool::name))
                .toList();

        PromptContract prompt = simulateAgentLoopPrompt(refreshedSnapshot);

        assertEquals(
                "mcp.task9e2e-alpha.search, mcp.task9e2e-beta.fetch_page, mcp.task9e2e-zeta.search",
                prompt.toolList()
        );
        assertEquals(
                "- mcp.task9e2e-beta.fetch_page: {\n  \"type\": \"object\"\n}",
                prompt.argsSchema()
        );
        assertEquals(
                List.of(
                        "### mcp.task9e2e-alpha.search",
                        "Description:\nAlpha description",
                        "### mcp.task9e2e-beta.fetch_page",
                        "Arguments Schema:\n{\n  \"type\": \"object\"\n}",
                        "Arguments Details:\n  - path: required string - Documentation path",
                        "### mcp.task9e2e-zeta.search"
                ),
                prompt.nonBlankSections()
        );
    }

    @Test
    void task9e2e_metadataOmission_survivesRegistryRefreshContract() {
        String agentLoopSource = readSource("base/core/src/main/java/space/controlnet/mineagent/core/agent/AgentLoop.java");

        assertContains(
                "task9e2e/prompt-order/description-uses-optional-helper",
                agentLoopSource,
                "tool.descriptionOptional().ifPresent(description -> sections.add(\"Description:\\n\" + description));"
        );
        assertContains(
                "task9e2e/prompt-order/args-schema-uses-optional-helper",
                agentLoopSource,
                "tool.argsSchemaOptional().ifPresent(schema -> sections.add(\"Arguments Schema:\\n\" + schema));"
        );
        assertContains(
                "task9e2e/prompt-order/args-details-use-optional-helper",
                agentLoopSource,
                "String argsDetails = buildSection(\"Arguments Details:\", tool.argsDescriptionOptional());"
        );
        assertContains(
                "task9e2e/prompt-order/return-details-use-optional-helper",
                agentLoopSource,
                "List<String> details = tool.resultDescriptionOptional();"
        );
        assertContains(
                "task9e2e/prompt-order/examples-use-optional-helper",
                agentLoopSource,
                "String examples = buildSection(\"Examples:\", tool.examplesOptional());"
        );
        assertNotContains(
                "task9e2e/prompt-order/no-args-none-sentinel",
                agentLoopSource,
                "Arguments Details:\\n  - (none)"
        );
        assertNotContains(
                "task9e2e/prompt-order/no-return-none-sentinel",
                agentLoopSource,
                "Return Details:\\n  - (none)"
        );
        assertNotContains(
                "task9e2e/prompt-order/no-examples-none-sentinel",
                agentLoopSource,
                "Examples:\\n  - (none)"
        );

        List<AgentTool> refreshedSnapshot = List.of(
                        metadataOnly(
                                "mcp.task9e2e-alpha.search",
                                "",
                                "",
                                List.of(),
                                "",
                                List.of(),
                                List.of()
                        ),
                        metadataOnly(
                                "mcp.task9e2e-beta.fetch_page",
                                "",
                                "",
                                List.of(),
                                "",
                                List.of(),
                                List.of()
                        ))
                .stream()
                .sorted(Comparator.comparing(AgentTool::name))
                .toList();

        PromptContract prompt = simulateAgentLoopPrompt(refreshedSnapshot);
        assertEquals(
                List.of(
                        "### mcp.task9e2e-alpha.search",
                        "### mcp.task9e2e-beta.fetch_page"
                ),
                prompt.nonBlankSections()
        );
    }

    private static AgentTool metadataOnly(String name, String description, String argsSchema,
                                          List<String> argsDescription, String resultSchema,
                                          List<String> resultDescription, List<String> examples) {
        return AgentToolSpec.metadataOnly(name, description, argsSchema, argsDescription, resultSchema,
                resultDescription, examples);
    }

    private static PromptContract simulateAgentLoopPrompt(List<AgentTool> tools) {
        String toolList = tools.stream()
                .map(AgentTool::name)
                .reduce((left, right) -> left + ", " + right)
                .orElse("");

        String argsSchema = tools.stream()
                .map(McpPromptToolOrderRegressionTest::buildArgsSchemaLine)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        String toolsSection = tools.stream()
                .map(McpPromptToolOrderRegressionTest::buildToolSection)
                .filter(section -> !section.isBlank())
                .reduce((left, right) -> left + "\n\n" + right)
                .orElse("");

        List<String> nonBlankSections = toolsSection.isBlank()
                ? List.of()
                : List.of(toolsSection.split("\\n\\n"));
        return new PromptContract(toolList, argsSchema, toolsSection, nonBlankSections);
    }

    private static String buildArgsSchemaLine(AgentTool tool) {
        if (tool == null) {
            return "";
        }
        return tool.argsSchemaOptional()
                .map(schema -> "- " + tool.name() + ": " + schema)
                .orElse("");
    }

    private static String buildSection(String header, List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(header);
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            builder.append("\n  - ").append(line);
        }
        return builder.toString();
    }

    private static String buildReturnSection(AgentTool tool) {
        if (tool == null) {
            return "";
        }
        Optional<String> schema = tool.resultSchemaOptional();
        List<String> details = tool.resultDescriptionOptional();
        if (schema.isEmpty() && details.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        builder.append("Return Details:");
        schema.ifPresent(value -> builder.append("\n  - ").append(value));
        for (String line : details) {
            builder.append("\n  - ").append(line);
        }
        return builder.toString();
    }

    private static String buildToolSection(AgentTool tool) {
        if (tool == null) {
            return "";
        }
        List<String> sections = new java.util.ArrayList<>();
        sections.add("### " + tool.name());
        tool.descriptionOptional().ifPresent(description -> sections.add("Description:\n" + description));
        tool.argsSchemaOptional().ifPresent(schema -> sections.add("Arguments Schema:\n" + schema));

        String argsDetails = buildSection("Arguments Details:", tool.argsDescriptionOptional());
        if (!argsDetails.isBlank()) {
            sections.add(argsDetails);
        }
        String returns = buildReturnSection(tool);
        if (!returns.isBlank()) {
            sections.add(returns);
        }
        String examples = buildSection("Examples:", tool.examplesOptional());
        if (!examples.isBlank()) {
            sections.add(examples);
        }
        return String.join("\n\n", sections).trim();
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

    private record PromptContract(String toolList, String argsSchema, String toolsSection, List<String> nonBlankSections) {
    }
}

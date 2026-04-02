package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.AgentToolSpec;

import java.lang.reflect.Method;
import java.util.List;

public final class AgentLoopPromptRuntimeRegressionTest {
    @Test
    void task18_buildToolPrompt_emptyInput_returnsEmptyPromptFragments() {
        Object nullPrompt = invokeBuildToolPrompt(null);
        assertEquals("task18/empty/null-tool-list", "", readPromptField(nullPrompt, "toolList"));
        assertEquals("task18/empty/null-args-schema", "", readPromptField(nullPrompt, "argsSchema"));
        assertEquals("task18/empty/null-tools-section", "", readPromptField(nullPrompt, "toolsSection"));

        Object emptyPrompt = invokeBuildToolPrompt(List.of());
        assertEquals("task18/empty/empty-tool-list", "", readPromptField(emptyPrompt, "toolList"));
        assertEquals("task18/empty/empty-args-schema", "", readPromptField(emptyPrompt, "argsSchema"));
        assertEquals("task18/empty/empty-tools-section", "", readPromptField(emptyPrompt, "toolsSection"));
    }

    @Test
    void task18_buildArgsSchemaLine_handlesNullAndBlankSchemas() {
        String nullTool = (String) invokeStatic("buildArgsSchemaLine", new Class<?>[]{AgentTool.class}, (Object) null);
        assertEquals("task18/args-schema/null-tool", "", nullTool);

        AgentTool blankSchema = AgentToolSpec.metadataOnly(
                "mcp.task18.blank",
                "desc",
                "",
                List.of(),
                "",
                List.of(),
                List.of()
        );
        String blank = (String) invokeStatic("buildArgsSchemaLine", new Class<?>[]{AgentTool.class}, blankSchema);
        assertEquals("task18/args-schema/blank-schema", "", blank);

        AgentTool schemaTool = AgentToolSpec.metadataOnly(
                "mcp.task18.fetch",
                "desc",
                "{\"type\":\"object\"}",
                List.of(),
                "",
                List.of(),
                List.of()
        );
        String rendered = (String) invokeStatic("buildArgsSchemaLine", new Class<?>[]{AgentTool.class}, schemaTool);
        assertEquals("task18/args-schema/with-schema", "- mcp.task18.fetch: {\"type\":\"object\"}", rendered);
    }

    @Test
    void task18_buildSection_andBuildReturnSection_respectOptionalLines() {
        String emptySection = (String) invokeStatic(
                "buildSection",
                new Class<?>[]{String.class, List.class},
                "Examples:",
                List.of()
        );
        assertEquals("task18/build-section/empty-lines", "", emptySection);

        String mixedSection = (String) invokeStatic(
                "buildSection",
                new Class<?>[]{String.class, List.class},
                "Arguments Details:",
                List.of("", "path: required string", "  ", "page: optional integer")
        );
        assertEquals(
                "task18/build-section/mixed-lines",
                "Arguments Details:\n  - path: required string\n  - page: optional integer",
                mixedSection
        );

        String noReturn = (String) invokeStatic("buildReturnSection", new Class<?>[]{AgentTool.class}, (Object) null);
        assertEquals("task18/build-return/null-tool", "", noReturn);

        AgentTool detailsOnly = AgentToolSpec.metadataOnly(
                "mcp.task18.inspect",
                "",
                "",
                List.of(),
                "",
                List.of("status: success or failure"),
                List.of()
        );
        String detailsOnlyRendered = (String) invokeStatic("buildReturnSection", new Class<?>[]{AgentTool.class}, detailsOnly);
        assertEquals(
                "task18/build-return/details-only",
                "Return Details:\n  - status: success or failure",
                detailsOnlyRendered
        );
    }

    @Test
    void task18_buildToolSection_andBuildToolPrompt_renderDeterministicRuntimePrompt() {
        AgentTool richTool = AgentToolSpec.metadataOnly(
                "mcp.task18.fetch_page",
                "Fetches a page",
                "{\"type\":\"object\"}",
                List.of("path: required string"),
                "{\"type\":\"object\"}",
                List.of("title: string", "body: string"),
                List.of("{\"path\":\"/guide\"}")
        );
        AgentTool minimalTool = AgentToolSpec.metadataOnly(
                "mcp.task18.search",
                "",
                "",
                List.of(),
                "",
                List.of(),
                List.of()
        );

        String section = (String) invokeStatic("buildToolSection", new Class<?>[]{AgentTool.class}, richTool);
        assertContains("task18/build-tool-section/header", section, "### mcp.task18.fetch_page");
        assertContains("task18/build-tool-section/description", section, "Description:\nFetches a page");
        assertContains("task18/build-tool-section/args-schema", section, "Arguments Schema:\n{\"type\":\"object\"}");
        assertContains("task18/build-tool-section/args-details", section, "Arguments Details:\n  - path: required string");
        assertContains("task18/build-tool-section/return-details", section, "Return Details:\n  - {\"type\":\"object\"}\n  - title: string\n  - body: string");
        assertContains("task18/build-tool-section/examples", section, "Examples:\n  - {\"path\":\"/guide\"}");

        Object prompt = invokeBuildToolPrompt(List.of(richTool, minimalTool));
        assertEquals(
                "task18/build-tool-prompt/tool-list-order",
                "mcp.task18.fetch_page, mcp.task18.search",
                readPromptField(prompt, "toolList")
        );
        assertEquals(
                "task18/build-tool-prompt/args-schema-only-nonblank",
                "- mcp.task18.fetch_page: {\"type\":\"object\"}",
                readPromptField(prompt, "argsSchema")
        );

        String toolsSection = readPromptField(prompt, "toolsSection");
        assertContains("task18/build-tool-prompt/rich-section-present", toolsSection, "### mcp.task18.fetch_page");
        assertContains("task18/build-tool-prompt/minimal-section-present", toolsSection, "### mcp.task18.search");
    }

    private static Object invokeBuildToolPrompt(List<AgentTool> tools) {
        return invokeStatic("buildToolPrompt", new Class<?>[]{List.class}, tools);
    }

    private static String readPromptField(Object toolPrompt, String field) {
        try {
            Method getter = toolPrompt.getClass().getDeclaredMethod(field);
            getter.setAccessible(true);
            Object value = getter.invoke(toolPrompt);
            return value == null ? null : value.toString();
        } catch (Exception exception) {
            throw new AssertionError("read-prompt-field failed: " + field, exception);
        }
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = AgentLoop.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception exception) {
            throw new AssertionError("invoke-static failed: " + methodName, exception);
        }
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}

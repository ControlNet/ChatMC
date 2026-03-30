package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ToolCallArgsParseBoundaryRegressionTest {
    @Test
    void task15_parserBoundary_exact65536Accepted() {
        String atBoundary = "a".repeat(ToolCallArgsParseBoundary.MAX_ARGS_JSON_LENGTH);
        ToolCallArgsParseBoundary.validate("mc.boundary.accept", atBoundary);
    }

    @Test
    void task15_parserBoundary_65537RejectedWithExplicitSignal() {
        String oversize = "a".repeat(ToolCallArgsParseBoundary.MAX_ARGS_JSON_LENGTH + 1);

        ToolCallArgsParseBoundary.ToolCallParseBoundaryException exception = assertThrows(
                "task15/parser/oversize/throws",
                ToolCallArgsParseBoundary.ToolCallParseBoundaryException.class,
                () -> ToolCallArgsParseBoundary.validate("mc.boundary.reject", oversize)
        );

        assertEquals(
                "task15/parser/oversize/message",
                "PARSE_BOUNDARY_TOOL_ARGS_TOO_LARGE: tool='mc.boundary.reject', argsJson.length=65537, max=65536",
                exception.getMessage()
        );
    }

    @Test
    void task15_parserBoundary_utfMultibyteEdge_usesUtf16LengthSemantics() {
        String emoji = "😀";
        assertEquals("task15/parser/utf/emoji-code-unit-length", 2, emoji.length());

        String atBoundary = emoji.repeat(ToolCallArgsParseBoundary.MAX_ARGS_JSON_LENGTH / emoji.length());
        assertEquals("task15/parser/utf/at-boundary-length", 65536, atBoundary.length());
        ToolCallArgsParseBoundary.validate("mc.boundary.utf.accept", atBoundary);

        String oversizeByOneCodeUnit = atBoundary + "a";
        assertEquals("task15/parser/utf/oversize-length", 65537, oversizeByOneCodeUnit.length());
        ToolCallArgsParseBoundary.ToolCallParseBoundaryException exception = assertThrows(
                "task15/parser/utf/oversize-throws",
                ToolCallArgsParseBoundary.ToolCallParseBoundaryException.class,
                () -> ToolCallArgsParseBoundary.validate("mc.boundary.utf.reject", oversizeByOneCodeUnit)
        );
        assertContains(
                "task15/parser/utf/oversize-signal",
                exception.getMessage(),
                "PARSE_BOUNDARY_TOOL_ARGS_TOO_LARGE: tool='mc.boundary.utf.reject', argsJson.length=65537, max=65536"
        );
    }

    @Test
    void task15_parserIntegration_contractsRetainBoundaryValidationAndMapping() {
        String reasoningService = readSource(
                "base/core/src/main/java/space/controlnet/mineagent/core/agent/AgentReasoningService.java"
        );
        assertContains(
                "task15/parser/integration/reasoning-validates",
                reasoningService,
                "ToolCallArgsParseBoundary.validate(tool, argsJson);"
        );
        assertContains(
                "task15/parser/integration/reasoning-catches-boundary",
                reasoningService,
                "catch (ToolCallArgsParseBoundary.ToolCallParseBoundaryException e)"
        );
        assertContains(
                "task15/parser/integration/reasoning-maps-to-empty",
                reasoningService,
                "return Optional.empty();"
        );

        String langChainParser = readSource(
                "base/core/src/main/java/space/controlnet/mineagent/core/agent/LangChainToolCallParser.java"
        );
        assertContains(
                "task15/parser/integration/langchain-validates",
                langChainParser,
                "ToolCallArgsParseBoundary.validate(tool, argsJson);"
        );
        assertContains(
                "task15/parser/integration/langchain-catches-boundary",
                langChainParser,
                "catch (ToolCallArgsParseBoundary.ToolCallParseBoundaryException e)"
        );
        assertContains(
                "task15/parser/integration/langchain-maps-to-empty",
                langChainParser,
                "return Optional.empty();"
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

    private static <T extends Throwable> T assertThrows(
            String assertionName,
            Class<T> expectedType,
            ThrowingRunnable runnable
    ) {
        try {
            runnable.run();
        } catch (Throwable throwable) {
            if (expectedType.isInstance(throwable)) {
                return expectedType.cast(throwable);
            }
            throw new AssertionError(
                    assertionName + " -> expected " + expectedType.getName() + " but got " + throwable.getClass().getName(),
                    throwable
            );
        }
        throw new AssertionError(assertionName + " -> expected exception " + expectedType.getName());
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (!haystack.contains(needle)) {
            throw new AssertionError(assertionName + " -> expected to find: " + needle);
        }
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }
}

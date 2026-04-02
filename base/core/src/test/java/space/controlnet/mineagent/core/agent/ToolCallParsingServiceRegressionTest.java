package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.ParseOutcome;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ToolCallParsingServiceRegressionTest {
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000001701");

    @Test
    void task18_toolCallParsing_emptyPrompt_returnsStableEmptySignal() {
        ParseOutcome outcome = ToolCallParsingService.parseAsync(
                PLAYER_ID,
                "   ",
                new ReflectiveToolCallParser(new RecordingLogger()),
                Runnable::run,
                50,
                new LlmRateLimiter(0)
        ).join();

        assertEquals("task18/tool-parsing/empty/call", null, outcome.call());
        assertEquals("task18/tool-parsing/empty/code", "empty", outcome.errorCode());
        assertEquals("task18/tool-parsing/empty/message", "Empty command", outcome.errorMessage());
    }

    @Test
    void task18_toolCallParsing_localCommandShortcut_usesLastUserMarkerSegment() {
        ParseOutcome outcome = ToolCallParsingService.parseAsync(
                PLAYER_ID,
                "System context\nUser:   MC.FIND_RECIPES minecraft:chest   ",
                new ReflectiveToolCallParser(new RecordingLogger()),
                Runnable::run,
                50,
                new LlmRateLimiter(0)
        ).join();

        assertEquals("task18/tool-parsing/local/code", null, outcome.errorCode());
        assertEquals("task18/tool-parsing/local/tool", "mc.find_recipes", outcome.call().toolName());
        assertContains("task18/tool-parsing/local/args-item", outcome.call().argsJson(), "minecraft:chest");
        assertContains("task18/tool-parsing/local/args-limit", outcome.call().argsJson(), "\"limit\":10");
    }

    @Test
    void task18_toolCallParsing_llmUnavailable_returnsStableError() {
        LlmRuntime.clear();
        ParseOutcome outcome = ToolCallParsingService.parseAsync(
                PLAYER_ID,
                "translate this into a tool call",
                new ReflectiveToolCallParser(new RecordingLogger()),
                Runnable::run,
                50,
                new LlmRateLimiter(0)
        ).join();

        assertEquals("task18/tool-parsing/unavailable/call", null, outcome.call());
        assertEquals("task18/tool-parsing/unavailable/code", "llm_unavailable", outcome.errorCode());
        assertEquals("task18/tool-parsing/unavailable/message", "LLM unavailable", outcome.errorMessage());
    }

    @Test
    void task18_toolCallParsing_rateLimited_returnsStableErrorBeforeDispatch() {
        ReflectiveToolCallParser parser = parserWithStub(prompt -> Optional.of(new ToolCall("mc.find_recipes", "{}")));
        LlmRateLimiter limiter = new LlmRateLimiter(60_000L);
        assertTrue("task18/tool-parsing/rate-limit/preconsume", limiter.allow(PLAYER_ID));

        ParseOutcome outcome = ToolCallParsingService.parseAsync(
                PLAYER_ID,
                "find recipes",
                parser,
                Runnable::run,
                50,
                limiter
        ).join();

        assertEquals("task18/tool-parsing/rate-limit/call", null, outcome.call());
        assertEquals("task18/tool-parsing/rate-limit/code", "llm_rate_limited", outcome.errorCode());
        assertEquals("task18/tool-parsing/rate-limit/message", "LLM rate limit exceeded", outcome.errorMessage());
    }

    @Test
    void task18_toolCallParsing_unknownCommand_returnsStableError() {
        ReflectiveToolCallParser parser = parserWithStub(prompt -> Optional.empty());

        ParseOutcome outcome = ToolCallParsingService.parseAsync(
                PLAYER_ID,
                "some unknown intent",
                parser,
                Runnable::run,
                50,
                new LlmRateLimiter(0)
        ).join();

        assertEquals("task18/tool-parsing/unknown/call", null, outcome.call());
        assertEquals("task18/tool-parsing/unknown/code", "unknown_command", outcome.errorCode());
        assertEquals("task18/tool-parsing/unknown/message", "Unknown command", outcome.errorMessage());
    }

    @Test
    void task18_toolCallParsing_timeout_returnsStableError() {
        ReflectiveToolCallParser parser = parserWithStub(prompt -> {
            sleep(100);
            return Optional.of(new ToolCall("mc.find_recipes", "{}"));
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ParseOutcome outcome = ToolCallParsingService.parseAsync(
                    PLAYER_ID,
                    "timed request",
                    parser,
                    executor,
                    10,
                    new LlmRateLimiter(0)
            ).join();

            assertEquals("task18/tool-parsing/timeout/call", null, outcome.call());
            assertEquals("task18/tool-parsing/timeout/code", "llm_timeout", outcome.errorCode());
            assertEquals("task18/tool-parsing/timeout/message", "LLM request timed out", outcome.errorMessage());
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void task18_toolCallParsing_llmFailure_returnsStableError() {
        ReflectiveToolCallParser parser = parserWithStub(prompt -> {
            throw new IllegalStateException("boom");
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            ParseOutcome outcome = ToolCallParsingService.parseAsync(
                    PLAYER_ID,
                    "broken request",
                    parser,
                    executor,
                    50,
                    new LlmRateLimiter(0)
            ).join();

            assertEquals("task18/tool-parsing/failure/call", null, outcome.call());
            assertEquals("task18/tool-parsing/failure/code", "llm_failed", outcome.errorCode());
            assertEquals("task18/tool-parsing/failure/message", "LLM request failed", outcome.errorMessage());
        } finally {
            shutdown(executor);
        }
    }

    @Test
    void task18_toolCallParsing_success_returnsParsedToolCall() {
        ReflectiveToolCallParser parser = parserWithStub(prompt -> Optional.of(new ToolCall("mc.find_usage", "{\"itemId\":\"minecraft:stick\"}")));

        ParseOutcome outcome = ToolCallParsingService.parseAsync(
                PLAYER_ID,
                "please find usage",
                parser,
                Runnable::run,
                50,
                new LlmRateLimiter(0)
        ).join();

        assertEquals("task18/tool-parsing/success/code", null, outcome.errorCode());
        assertEquals("task18/tool-parsing/success/tool", "mc.find_usage", outcome.call().toolName());
        assertContains("task18/tool-parsing/success/args", outcome.call().argsJson(), "minecraft:stick");
    }

    private static ReflectiveToolCallParser parserWithStub(ToolCallParser parser) {
        try {
            ReflectiveToolCallParser reflective = new ReflectiveToolCallParser(new RecordingLogger());
            Field parserField = ReflectiveToolCallParser.class.getDeclaredField("parser");
            parserField.setAccessible(true);
            parserField.set(reflective, parser);
            Field attemptedField = ReflectiveToolCallParser.class.getDeclaredField("initAttempted");
            attemptedField.setAccessible(true);
            attemptedField.setBoolean(reflective, true);
            return reflective;
        } catch (Exception exception) {
            throw new AssertionError("task18/tool-parsing/parser-injection", exception);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("task18/tool-parsing/sleep-interrupted", exception);
        }
    }

    private static void shutdown(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("task18/tool-parsing/executor-shutdown", exception);
        }
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class RecordingLogger implements Logger {
        private final List<String> messages = new ArrayList<>();

        @Override
        public void warn(String message, Throwable exception) {
            messages.add(message);
        }
    }
}

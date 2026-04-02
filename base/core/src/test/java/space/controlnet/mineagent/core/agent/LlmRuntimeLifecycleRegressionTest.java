package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

public final class LlmRuntimeLifecycleRegressionTest {
    @Test
    void task21_llmModelFactory_build_requiresApiKey_andBuildsWithDirectKey() {
        LlmConfig missingKey = config(Optional.empty(), Optional.empty(), Optional.empty());
        LlmConfig directKey = config(Optional.of("unit-test-key"), Optional.empty(), Optional.of(64));

        assertEquals("task21/factory/missing-key-empty", Optional.empty(), LlmModelFactory.build(missingKey));
        assertTrue("task21/factory/direct-key-model-present", LlmModelFactory.build(directKey).isPresent());
    }

    @Test
    void task21_llmRuntime_clear_resetsModelReference() {
        LlmRuntime.clear();
        assertEquals("task21/runtime/clear", Optional.empty(), LlmRuntime.model());
    }

    @Test
    void task21_llmRuntimeManager_reload_success_updatesRuntimeAndCallbacks() {
        LlmRuntime.clear();

        RecordingHandler handler = new RecordingHandler();
        LlmConfig config = config(Optional.of("unit-test-key"), Optional.of("https://example.invalid/v1"), Optional.of(256));

        boolean reloaded = LlmRuntimeManager.reload(config, handler);

        assertTrue("task21/runtime-manager/reloaded", reloaded);
        assertTrue("task21/runtime/model-present", LlmRuntime.model().isPresent());
        assertEquals("task21/runtime/cooldown", config.cooldownMillis(), handler.cooldownMillis);
        assertEquals("task21/runtime/timeout", config.timeout().toMillis(), handler.timeoutMillis);
        assertEquals("task21/runtime/max-tool-calls", config.maxToolCalls(), handler.maxToolCalls);
        assertEquals("task21/runtime/max-iterations", config.maxIterations(), handler.maxIterations);
        assertEquals("task21/runtime/log-responses", config.logResponses(), handler.logResponses);
        assertEquals("task21/runtime/max-retries", config.maxRetries(), handler.maxRetries);
        assertEquals("task21/runtime/no-failure-message", null, handler.failureMessage);
    }

    @Test
    void task21_llmRuntimeManager_reload_failure_reportsStableErrorMessage() {
        LlmRuntime.clear();

        RecordingHandler handler = new RecordingHandler();
        LlmConfig missingKey = config(Optional.empty(), Optional.empty(), Optional.empty());

        boolean reloaded = LlmRuntimeManager.reload(missingKey, handler);

        assertFalse("task21/runtime-manager/reloaded-false", reloaded);
        assertEquals(
                "task21/runtime-manager/failure-message",
                "Failed to initialize LLM model. Check provider configuration.",
                handler.failureMessage
        );
        assertEquals("task21/runtime-manager/model-still-empty", Optional.empty(), LlmRuntime.model());
    }

    @Test
    void task21_llmModelFactory_privateHelpers_coverTokenSetterBranches() {
        assertFalse("task21/helpers/is-gpt5-null", (Boolean) invokeStatic("isGpt5Model", new Class<?>[]{String.class}, (Object) null));
        assertTrue("task21/helpers/is-gpt5-true", (Boolean) invokeStatic("isGpt5Model", new Class<?>[]{String.class}, "gpt-5-nano"));

        CompletionBuilder completionBuilder = new CompletionBuilder();
        invokeStatic(
                "applyOpenAiMaxTokens",
                new Class<?>[]{Object.class, Optional.class, String.class},
                completionBuilder,
                Optional.of(123),
                "gpt-4o-mini"
        );
        assertEquals("task21/helpers/completion-token-used", 123, completionBuilder.maxCompletionTokens);
        assertEquals("task21/helpers/max-tokens-not-used-when-completion-exists", -1, completionBuilder.maxTokens);

        IntegerOnlyBuilder integerOnlyBuilder = new IntegerOnlyBuilder();
        invokeStatic(
                "applyOpenAiMaxTokens",
                new Class<?>[]{Object.class, Optional.class, String.class},
                integerOnlyBuilder,
                Optional.of(77),
                "gpt-4o-mini"
        );
        assertEquals("task21/helpers/integer-setter-fallback", 77, integerOnlyBuilder.maxTokensInteger);

        IntOnlyBuilder gpt5Builder = new IntOnlyBuilder();
        invokeStatic(
                "applyOpenAiMaxTokens",
                new Class<?>[]{Object.class, Optional.class, String.class},
                gpt5Builder,
                Optional.of(66),
                "gpt-5-large"
        );
        assertEquals("task21/helpers/gpt5-skips-maxTokens", -1, gpt5Builder.maxTokens);
    }

    private static LlmConfig config(Optional<String> apiKey, Optional<String> baseUrl, Optional<Integer> maxTokens) {
        return new LlmConfig(
                LlmProvider.OPENAI,
                "gpt-4o-mini",
                baseUrl,
                apiKey,
                Optional.empty(),
                Optional.of(0.2d),
                Optional.of(0.8d),
                maxTokens,
                Duration.ofSeconds(3),
                2,
                1500L,
                25,
                30,
                true,
                true,
                true
        );
    }

    private static Object invokeStatic(String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = LlmModelFactory.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, args);
        } catch (Exception exception) {
            throw new AssertionError("task21/invoke-static/" + methodName, exception);
        }
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static void assertFalse(String assertionName, boolean value) {
        if (!value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected false");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class CompletionBuilder {
        private int maxCompletionTokens = -1;
        private int maxTokens = -1;

        public CompletionBuilder maxCompletionTokens(int value) {
            this.maxCompletionTokens = value;
            return this;
        }

        public CompletionBuilder maxTokens(int value) {
            this.maxTokens = value;
            return this;
        }
    }

    private static final class IntegerOnlyBuilder {
        private int maxTokensInteger = -1;

        public IntegerOnlyBuilder maxTokens(Integer value) {
            this.maxTokensInteger = value == null ? -1 : value;
            return this;
        }
    }

    private static final class IntOnlyBuilder {
        private int maxTokens = -1;

        public IntOnlyBuilder maxTokens(int value) {
            this.maxTokens = value;
            return this;
        }
    }

    private static final class RecordingHandler implements LlmRuntimeManager.RuntimeEventHandler {
        private long cooldownMillis;
        private long timeoutMillis;
        private int maxToolCalls;
        private int maxIterations;
        private boolean logResponses;
        private int maxRetries;
        private String failureMessage;

        @Override
        public void onCooldownUpdated(long cooldownMillis) {
            this.cooldownMillis = cooldownMillis;
        }

        @Override
        public void onTimeoutUpdated(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public void onMaxToolCallsUpdated(int maxToolCalls) {
            this.maxToolCalls = maxToolCalls;
        }

        @Override
        public void onMaxIterationsUpdated(int maxIterations) {
            this.maxIterations = maxIterations;
        }

        @Override
        public void onLogResponsesUpdated(boolean logResponses) {
            this.logResponses = logResponses;
        }

        @Override
        public void onMaxRetriesUpdated(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        @Override
        public void onReloadFailed(String message) {
            this.failureMessage = message;
        }
    }
}

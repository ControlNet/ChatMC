package space.controlnet.mineagent.core.agent;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public final class LlmConfigParserRegressionTest {
    @Test
    void task18_llmConfigParser_nullAndInvalidToml_fallBackToDefaults() {
        LlmConfig defaults = customDefaults();

        assertEquals("task18/llm-config/null-reader", defaults, LlmConfigParser.parse(null, defaults));
        assertEquals(
                "task18/llm-config/invalid-toml",
                defaults,
                LlmConfigParser.parse(new StringReader("provider = [oops"), defaults)
        );
    }

    @Test
    void task18_llmConfigParser_validToml_parsesOverridesAndProviderFallback() {
        LlmConfig parsed = LlmConfigParser.parse(new StringReader("""
                provider = "invalid-provider"
                model = "gpt-5"
                baseUrl = "https://example.invalid/v1"
                apiKey = "test-key"
                apiKeyEnv = "OPENAI_TOKEN"
                temperature = 0.6
                topP = 0.8
                maxTokens = 4096
                timeoutSeconds = 45
                maxRetries = 3
                cooldownMillis = 250
                maxToolCalls = 7
                maxIterations = 11
                strictJsonSchema = false
                logRequests = true
                logResponses = true
                """), LlmConfig.defaults());

        assertEquals("task18/llm-config/provider-fallback", LlmProvider.OPENAI, parsed.provider());
        assertEquals("task18/llm-config/model", "gpt-5", parsed.model());
        assertEquals("task18/llm-config/base-url", Optional.of("https://example.invalid/v1"), parsed.baseUrl());
        assertEquals("task18/llm-config/api-key", Optional.of("test-key"), parsed.apiKey());
        assertEquals("task18/llm-config/api-key-env", Optional.of("OPENAI_TOKEN"), parsed.apiKeyEnv());
        assertEquals("task18/llm-config/temperature", Optional.of(0.6D), parsed.temperature());
        assertEquals("task18/llm-config/top-p", Optional.of(0.8D), parsed.topP());
        assertEquals("task18/llm-config/max-tokens", Optional.of(4096), parsed.maxTokens());
        assertEquals("task18/llm-config/timeout", Duration.ofSeconds(45), parsed.timeout());
        assertEquals("task18/llm-config/max-retries", 3, parsed.maxRetries());
        assertEquals("task18/llm-config/cooldown", 250L, parsed.cooldownMillis());
        assertEquals("task18/llm-config/max-tool-calls", 7, parsed.maxToolCalls());
        assertEquals("task18/llm-config/max-iterations", 11, parsed.maxIterations());
        assertEquals("task18/llm-config/strict", false, parsed.strictJsonSchema());
        assertEquals("task18/llm-config/log-requests", true, parsed.logRequests());
        assertEquals("task18/llm-config/log-responses", true, parsed.logResponses());
    }

    @Test
    void task18_llmConfigParser_writeToml_rendersEscapedValuesAndCommentedOptionals() {
        LlmConfig config = new LlmConfig(
                LlmProvider.OPENAI,
                "gpt-5-\"quoted\"",
                Optional.of("https://example.invalid/\\v1"),
                Optional.empty(),
                Optional.of("OPENAI_TOKEN"),
                Optional.empty(),
                Optional.of(0.9D),
                Optional.empty(),
                Duration.ofSeconds(99),
                2,
                123L,
                5,
                6,
                true,
                true,
                false
        );
        StringWriter writer = new StringWriter();

        LlmConfigParser.writeToml(writer, config);

        String toml = writer.toString();
        assertContains("task18/llm-config/write/header", toml, "# MineAgent LLM configuration");
        assertContains("task18/llm-config/write/model-escaped", toml, "model = \"gpt-5-\\\"quoted\\\"\"");
        assertContains("task18/llm-config/write/base-url-escaped", toml, "baseUrl = \"https://example.invalid/\\\\v1\"");
        assertContains("task18/llm-config/write-commented-api-key", toml, "#apiKey = \"\"");
        assertContains("task18/llm-config/write-commented-temperature", toml, "#temperature = 0.0");
        assertContains("task18/llm-config/write-top-p", toml, "topP = 0.9");
        assertContains("task18/llm-config/write-timeout", toml, "timeoutSeconds = 99");
    }

    @Test
    void task18_llmConfigValidator_reportsAllIndependentFailuresAndAcceptsValidConfig() {
        List<String> nullErrors = LlmConfigValidator.validate(null);
        assertEquals("task18/llm-config/validator-null", List.of("LLM config is missing."), nullErrors);

        LlmConfig invalid = new LlmConfig(
                LlmProvider.OPENAI,
                " ",
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Duration.ZERO,
                1,
                0L,
                0,
                -1,
                true,
                false,
                false
        );

        assertEquals(
                "task18/llm-config/validator-invalid",
                List.of(
                        "model is required.",
                        "OPENAI requires apiKey or apiKeyEnv.",
                        "timeoutSeconds must be > 0.",
                        "maxToolCalls must be > 0.",
                        "maxIterations must be > 0."
                ),
                LlmConfigValidator.validate(invalid)
        );

        LlmConfig valid = new LlmConfig(
                LlmProvider.OPENAI,
                "gpt-5-nano",
                Optional.empty(),
                Optional.empty(),
                Optional.of("OPENAI_TOKEN"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Duration.ofSeconds(30),
                1,
                0L,
                10,
                10,
                true,
                false,
                false
        );

        assertEquals("task18/llm-config/validator-valid", List.of(), LlmConfigValidator.validate(valid));
    }

    private static LlmConfig customDefaults() {
        return new LlmConfig(
                LlmProvider.OPENAI,
                "default-model",
                Optional.of("https://defaults.invalid/v1"),
                Optional.empty(),
                Optional.of("DEFAULT_KEY_ENV"),
                Optional.of(0.1D),
                Optional.of(0.5D),
                Optional.of(2048),
                Duration.ofSeconds(60),
                4,
                12L,
                22,
                33,
                false,
                true,
                true
        );
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}

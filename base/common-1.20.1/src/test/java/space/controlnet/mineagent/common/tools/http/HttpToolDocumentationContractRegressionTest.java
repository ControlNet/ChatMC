package space.controlnet.mineagent.common.tools.http;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class HttpToolDocumentationContractRegressionTest {
    private static final String REPO_FILE_NAME = "REPO.md";
    private static final String HTTP_SECTION_HEADER = "### 16.8 Built-in `http` tool contract (updated 2026-04-03)";

    @Test
    void taskHttp_docs_commandsAndDefaults_matchImplementation() throws Exception {
        String repoDocument = readRepoDocument();

        assertContains(repoDocument, HTTP_SECTION_HEADER);
        assertContains(repoDocument, "- `http` is a first-party built-in tool from `base/common-1.20.1`.");
        assertContains(repoDocument, "- Execution affinity is `CALLING_THREAD`, so outbound network I/O stays off the Minecraft server thread.");
        assertContains(repoDocument, fencedBlock("text", HttpToolMetadata.ARGS_SCHEMA));
        assertContains(repoDocument, fencedBlock("text", HttpToolMetadata.RESULT_SCHEMA));

        assertContains(repoDocument, "- `timeoutMs`: default `" + HttpToolRequest.DEFAULT_TIMEOUT_MS
                + "`, allowed range `" + HttpToolRequest.MIN_TIMEOUT_MS + ".." + HttpToolRequest.MAX_TIMEOUT_MS + "`");
        assertContains(repoDocument, "- `followRedirects`: default `" + HttpToolRequest.DEFAULT_FOLLOW_REDIRECTS + "`");
        assertContains(repoDocument, "- `maxRedirects`: default `" + HttpToolRequest.DEFAULT_MAX_REDIRECTS
                + "`, allowed range `0.." + HttpToolRequest.MAX_REDIRECTS + "`");
        assertContains(repoDocument, "- `responseMode`: default `" + HttpToolRequest.DEFAULT_RESPONSE_MODE + "`");
        assertContains(repoDocument, "- Request body size after UTF-8 or base64 decoding must be `<= "
                + HttpToolRequest.MAX_REQUEST_BODY_BYTES + "` bytes.");
        assertContains(repoDocument, "- Response body buffering is capped at `" + responseBodyLimitBytes()
                + "` bytes; overflow returns `failure.code = response_too_large` and sets top-level `truncated = true`.");
        assertContains(repoDocument, "- Completed `1xx` / `2xx` / `3xx` / `4xx` / `5xx` exchanges populate `response`; local validation, timeout, transport, and runtime failures populate `failure`.");
        assertContains(repoDocument, "- The success envelope records only `finalUrl` and `redirectCount`; v1 does not return full redirect history.");

        List<String> expectedCommands = List.of(
                gradleHttpTestCommand("HttpToolContractRegressionTest"),
                gradleHttpTestCommand("HttpToolRegistrationRegressionTest"),
                gradleHttpTestCommand("HttpToolThreadAffinityRegressionTest"),
                gradleHttpTestCommand("HttpToolFixtureRegressionTest"),
                gradleHttpTestCommand("HttpToolProviderValidationRegressionTest"),
                gradleHttpTestCommand("HttpToolExecutionRegressionTest"),
                gradleHttpTestCommand("HttpToolRenderingRegressionTest"),
                gradleHttpTestCommand("HttpToolDocumentationContractRegressionTest")
        );
        for (String expectedCommand : expectedCommands) {
            assertContains(repoDocument, expectedCommand);
        }
        assertEquals(8, expectedCommands.size());
    }

    @Test
    void taskHttp_docs_nonGoalsMatchCurrentScope() throws Exception {
        String repoDocument = readRepoDocument();

        assertContains(repoDocument, "- SSRF / allowlist / approval-flow hardening, secret-redaction frameworks, and broader request-safety policy work");
        assertContains(repoDocument, "- cookie jar persistence across tool invocations");
        assertContains(repoDocument, "- streaming, SSE, or WebSocket support");
        assertContains(repoDocument, "- multipart/form-data helpers, retry frameworks, proxy support, TLS customization, or download-to-file behavior");
        assertContains(repoDocument, "- dedicated response-compression helpers beyond default Java `HttpClient` behavior");
        assertContains(repoDocument, "- V1 is stateless across invocations: response `Set-Cookie` values are not persisted into later `Cookie` request headers.");

        assertFalse(repoDocument.contains("SSRF protection is enabled for the `http` tool."));
        assertFalse(repoDocument.contains("Cookies are persisted between `http` tool invocations."));
        assertFalse(repoDocument.contains("The `http` tool supports streaming responses in v1."));
    }

    private static String readRepoDocument() throws IOException {
        return Files.readString(resolveRepoFile());
    }

    private static Path resolveRepoFile() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(REPO_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new AssertionError("Could not locate REPO.md from test working directory.");
    }

    private static String fencedBlock(String language, String content) {
        return "```" + language + "\n" + content + "\n```";
    }

    private static int responseBodyLimitBytes() throws ReflectiveOperationException {
        Field field = HttpToolExecution.class.getDeclaredField("MAX_RESPONSE_BODY_BYTES");
        field.setAccessible(true);
        return field.getInt(null);
    }

    private static String gradleHttpTestCommand(String simpleClassName) {
        return "./gradlew --no-daemon --configure-on-demand :base:common-1.20.1:test --tests 'space.controlnet.mineagent.common.tools.http."
                + simpleClassName + "'";
    }

    private static void assertContains(String repoDocument, String expectedSnippet) {
        assertTrue(repoDocument.contains(expectedSnippet), () -> "Missing REPO.md snippet:\n" + expectedSnippet);
    }
}

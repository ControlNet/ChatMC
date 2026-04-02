package space.controlnet.mineagent.core.tools.mcp;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McpConfigValidatorEdgeRegressionTest {
    @Test
    void task18_mcpValidator_nullAliasAndMissingType_pathsReportStableMessages() {
        assertEquals(
                "task18/mcp-validator/null-config",
                List.of("MCP config is missing."),
                McpConfigValidator.validate(null)
        );

        LinkedHashMap<String, McpServerConfig> servers = new LinkedHashMap<>();
        servers.put("Bad_Alias", null);
        servers.put("valid-alias", new McpServerConfig(null, Optional.empty(), List.of(), Map.of(), Optional.empty(), Optional.empty()));

        List<String> errors = McpConfigValidator.validate(new McpConfig(servers));

        assertContains("task18/mcp-validator/invalid-alias",
                errors,
                "Server alias 'Bad_Alias' must match ^[a-z0-9][a-z0-9-]{0,31}$.");
        assertContains("task18/mcp-validator/missing-server-config",
                errors,
                "Server 'Bad_Alias' config is missing.");
        assertContains("task18/mcp-validator/missing-type",
                errors,
                "Server 'valid-alias' type is required.");
    }

    @Test
    void task18_mcpValidator_stdioAndHttpConstraintViolations_reportAllExpectedMessages() {
        McpServerConfig stdio = new McpServerConfig(
                McpTransportKind.STDIO,
                Optional.of("   "),
                List.of(),
                Map.of(),
                Optional.of("   "),
                Optional.of("https://not-allowed")
        );
        McpServerConfig http = new McpServerConfig(
                McpTransportKind.HTTP,
                Optional.of("python3"),
                List.of("server.py"),
                Map.of("TOKEN", "x"),
                Optional.of("/tmp"),
                Optional.of("ftp://example.com/mcp")
        );

        List<String> errors = McpConfigValidator.validate(new McpConfig(Map.of(
                "stdio-alpha", stdio,
                "http-beta", http
        )));

        assertContains("task18/mcp-validator/stdio-command-required", errors,
                "Server 'stdio-alpha' command is required for stdio.");
        assertContains("task18/mcp-validator/stdio-url-not-allowed", errors,
                "Server 'stdio-alpha' url is not allowed for stdio.");
        assertContains("task18/mcp-validator/stdio-cwd-blank", errors,
                "Server 'stdio-alpha' cwd must not be blank.");

        assertContains("task18/mcp-validator/http-url-scheme", errors,
                "Server 'http-beta' url must start with http:// or https://.");
        assertContains("task18/mcp-validator/http-command-disallowed", errors,
                "Server 'http-beta' command is not allowed for http.");
        assertContains("task18/mcp-validator/http-args-disallowed", errors,
                "Server 'http-beta' args is not allowed for http.");
        assertContains("task18/mcp-validator/http-env-disallowed", errors,
                "Server 'http-beta' env is not allowed for http.");
        assertContains("task18/mcp-validator/http-cwd-disallowed", errors,
                "Server 'http-beta' cwd is not allowed for http.");
    }

    @Test
    void task18_mcpValidator_httpUrlRequirement_blankUrlIsRejected() {
        McpServerConfig httpWithoutUrl = new McpServerConfig(
                McpTransportKind.HTTP,
                Optional.empty(),
                List.of(),
                Map.of(),
                Optional.empty(),
                Optional.of("   ")
        );

        List<String> errors = McpConfigValidator.validate(new McpConfig(Map.of("http-alpha", httpWithoutUrl)));

        assertContains("task18/mcp-validator/http-url-required", errors,
                "Server 'http-alpha' url is required for http.");
    }

    private static void assertContains(String assertionName, List<String> values, String expected) {
        if (values != null && values.contains(expected)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + expected + " in: " + values);
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }
}

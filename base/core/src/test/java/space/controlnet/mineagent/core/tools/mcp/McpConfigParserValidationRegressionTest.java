package space.controlnet.mineagent.core.tools.mcp;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McpConfigParserValidationRegressionTest {
    @Test
    void task1_mcpConfigParser_validStdioConfig_parsesSharedSubset() {
        McpConfig config = parse("""
                {
                  \"mcpServers\": {
                    \"stdio-alpha\": {
                      \"type\": \"stdio\",
                      \"command\": \"python3\",
                      \"args\": [\"server.py\", \"--readonly\"],
                      \"env\": {
                        \"MCP_MODE\": \"readonly\"
                      },
                      \"cwd\": \"/srv/mcp\"
                    }
                  }
                }
                """);

        assertEquals("task1/mcp-parser/stdio/errors", List.of(), McpConfigValidator.validate(config));

        McpServerConfig server = config.mcpServers().get("stdio-alpha");
        assertEquals("task1/mcp-parser/stdio/type", McpTransportKind.STDIO, server.type());
        assertEquals("task1/mcp-parser/stdio/command", Optional.of("python3"), server.command());
        assertEquals("task1/mcp-parser/stdio/args", List.of("server.py", "--readonly"), server.args());
        assertEquals("task1/mcp-parser/stdio/env", Map.of("MCP_MODE", "readonly"), server.env());
        assertEquals("task1/mcp-parser/stdio/cwd", Optional.of("/srv/mcp"), server.cwd());
        assertEquals("task1/mcp-parser/stdio/url", Optional.empty(), server.url());
    }

    @Test
    void task1_mcpConfigParser_validHttpConfig_parsesPublicHttpSubset() {
        McpConfig config = parse("""
                {
                  \"mcpServers\": {
                    \"public-http\": {
                      \"type\": \"http\",
                      \"url\": \"https://example.com/mcp\"
                    }
                  }
                }
                """);

        assertEquals("task1/mcp-parser/http/errors", List.of(), McpConfigValidator.validate(config));

        McpServerConfig server = config.mcpServers().get("public-http");
        assertEquals("task1/mcp-parser/http/type", McpTransportKind.HTTP, server.type());
        assertEquals("task1/mcp-parser/http/url", Optional.of("https://example.com/mcp"), server.url());
        assertEquals("task1/mcp-parser/http/command", Optional.empty(), server.command());
        assertEquals("task1/mcp-parser/http/args", List.of(), server.args());
        assertEquals("task1/mcp-parser/http/env", Map.of(), server.env());
        assertEquals("task1/mcp-parser/http/cwd", Optional.empty(), server.cwd());
    }

    @Test
    void task1_mcpConfigParser_invalidAlias_reportsStableValidationMessage() {
        McpConfig config = parse("""
                {
                  \"mcpServers\": {
                    \"Bad_Alias\": {
                      \"type\": \"stdio\",
                      \"command\": \"python3\"
                    }
                  }
                }
                """);

        assertEquals(
                "task1/mcp-parser/invalid-alias/errors",
                List.of("Server alias 'Bad_Alias' must match ^[a-z0-9][a-z0-9-]{0,31}$."),
                McpConfigValidator.validate(config)
        );
    }

    @Test
    void task1_mcpConfigParser_duplicateAlias_rejectedWithStableMessage() {
        IllegalArgumentException exception = assertThrows(
                "task1/mcp-parser/duplicate-alias/throws",
                IllegalArgumentException.class,
                () -> parse("""
                        {
                          "mcpServers": {
                            "dup-alpha": {
                              "type": "stdio",
                              "command": "python3"
                            },
                            "dup-alpha": {
                              "type": "http",
                              "url": "https://example.com/mcp"
                            }
                          }
                        }
                        """)
        );

        assertEquals(
                "task1/mcp-parser/duplicate-alias/message",
                "Duplicate server alias 'dup-alpha'.",
                exception.getMessage()
        );
    }

    @Test
    void task1_mcpConfigParser_unknownTopLevelKey_rejected() {
        IllegalArgumentException exception = assertThrows(
                "task1/mcp-parser/unknown-top-level/throws",
                IllegalArgumentException.class,
                () -> parse("""
                        {
                          \"mcpServers\": {},
                          \"version\": 1
                        }
                        """)
        );

        assertEquals("task1/mcp-parser/unknown-top-level/message", "Unknown top-level key: version.", exception.getMessage());
    }

    @Test
    void task1_mcpConfigParser_unknownServerKey_rejected() {
        IllegalArgumentException exception = assertThrows(
                "task1/mcp-parser/unknown-server-key/throws",
                IllegalArgumentException.class,
                () -> parse("""
                        {
                          \"mcpServers\": {
                            \"stdio-alpha\": {
                              \"type\": \"stdio\",
                              \"command\": \"python3\",
                              \"enabled\": true
                            }
                          }
                        }
                        """)
        );

        assertEquals(
                "task1/mcp-parser/unknown-server-key/message",
                "Server 'stdio-alpha' has unknown key 'enabled'.",
                exception.getMessage()
        );
    }

    @Test
    void task1_mcpConfigParser_nonSharedFields_rejected() {
        for (String field : List.of("oauth", "headers", "bearerTokenEnv", "env_vars")) {
            IllegalArgumentException exception = assertThrows(
                    "task1/mcp-parser/non-shared-field/" + field,
                    IllegalArgumentException.class,
                    () -> parse(jsonWithUnsupportedField(field))
            );

            assertEquals(
                    "task1/mcp-parser/non-shared-field/message/" + field,
                    "Server 'public-http' uses unsupported field '" + field + "'.",
                    exception.getMessage()
            );
        }
    }

    private static McpConfig parse(String json) {
        return McpConfigParser.parse(new StringReader(json));
    }

    private static String jsonWithUnsupportedField(String field) {
        return """
                {
                  \"mcpServers\": {
                    \"public-http\": {
                      \"type\": \"http\",
                      \"url\": \"https://example.com/mcp\",
                      \"%s\": true
                    }
                  }
                }
                """.formatted(field);
    }

    private static <T extends Throwable> T assertThrows(String assertionName, Class<T> expectedType, ThrowingRunnable runnable) {
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

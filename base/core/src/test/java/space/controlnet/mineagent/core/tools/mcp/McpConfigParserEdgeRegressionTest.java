package space.controlnet.mineagent.core.tools.mcp;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class McpConfigParserEdgeRegressionTest {
    @Test
    void task18_mcpParser_rootAndTopLevelGuards_reportStableMessages() {
        assertEquals(
                "task18/mcp-parser/root-not-object",
                "Root config must be a JSON object.",
                assertThrows("task18/mcp-parser/root-not-object/throws", IllegalArgumentException.class,
                        () -> parse("[]")).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/missing-mcpServers",
                "mcpServers is required.",
                assertThrows("task18/mcp-parser/missing-mcpServers/throws", IllegalArgumentException.class,
                        () -> parse("{}")).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/duplicate-top-level",
                "Duplicate top-level key: mcpServers.",
                assertThrows("task18/mcp-parser/duplicate-top-level/throws", IllegalArgumentException.class,
                        () -> parse("""
                                {
                                  "mcpServers": {},
                                  "mcpServers": {}
                                }
                                """)).getMessage()
        );
    }

    @Test
    void task18_mcpParser_serverShapeAndTypeGuards_reportStableMessages() {
        assertEquals(
                "task18/mcp-parser/mcpServers-type",
                "mcpServers must be a JSON object.",
                assertThrows("task18/mcp-parser/mcpServers-type/throws", IllegalArgumentException.class,
                        () -> parse("{\"mcpServers\":[]}")).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/server-not-object",
                "Server 'alpha' must be a JSON object.",
                assertThrows("task18/mcp-parser/server-not-object/throws", IllegalArgumentException.class,
                        () -> parse("{\"mcpServers\":{\"alpha\":42}} ")).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/duplicate-server-field",
                "Server 'alpha' has duplicate field 'type'.",
                assertThrows("task18/mcp-parser/duplicate-server-field/throws", IllegalArgumentException.class,
                        () -> parse("""
                                {
                                  "mcpServers": {
                                    "alpha": {
                                      "type": "stdio",
                                      "type": "http",
                                      "url": "https://example.com/mcp"
                                    }
                                  }
                                }
                                """)).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/unsupported-type",
                "Server 'alpha' has unsupported type 'websocket'.",
                assertThrows("task18/mcp-parser/unsupported-type/throws", IllegalArgumentException.class,
                        () -> parse("""
                                {
                                  "mcpServers": {
                                    "alpha": {
                                      "type": "websocket"
                                    }
                                  }
                                }
                                """)).getMessage()
        );
    }

    @Test
    void task18_mcpParser_stringTypeGuards_reportStableMessages() {
        assertEquals(
                "task18/mcp-parser/command-string",
                "Server 'alpha' field 'command' must be a string.",
                assertThrows("task18/mcp-parser/command-string/throws", IllegalArgumentException.class,
                        () -> parse("""
                                {
                                  "mcpServers": {
                                    "alpha": {
                                      "type": "stdio",
                                      "command": null
                                    }
                                  }
                                }
                                """)).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/args-array-strings",
                "Server 'alpha' field 'args' must be an array of strings.",
                assertThrows("task18/mcp-parser/args-array-strings/throws", IllegalArgumentException.class,
                        () -> parse("""
                                {
                                  "mcpServers": {
                                    "alpha": {
                                      "type": "stdio",
                                      "command": "python3",
                                      "args": ["ok", null]
                                    }
                                  }
                                }
                                """)).getMessage()
        );
        assertEquals(
                "task18/mcp-parser/env-string-map",
                "Server 'alpha' field 'env' must be an object of strings.",
                assertThrows("task18/mcp-parser/env-string-map/throws", IllegalArgumentException.class,
                        () -> parse("""
                                {
                                  "mcpServers": {
                                    "alpha": {
                                      "type": "stdio",
                                      "command": "python3",
                                      "env": {"A": null}
                                    }
                                  }
                                }
                                """)).getMessage()
        );
    }

    @Test
    void task18_mcpParser_writeJson_defaultsAndRoundTrip_areDeterministic() {
        StringWriter defaultsWriter = new StringWriter();
        McpConfigParser.writeJson(defaultsWriter, null);
        assertContains("task18/mcp-parser/write-defaults", defaultsWriter.toString(), "\"mcpServers\": {}");

        LinkedHashMap<String, McpServerConfig> servers = new LinkedHashMap<>();
        servers.put("stdio-alpha", McpServerConfig.stdio("python3", List.of("server.py"), Map.of("MODE", "readonly"), Optional.of("/srv/mcp")));
        servers.put("http-beta", McpServerConfig.http("https://example.com/mcp"));

        McpConfig config = new McpConfig(servers);
        StringWriter writer = new StringWriter();
        McpConfigParser.writeJson(writer, config);

        McpConfig roundTrip = parse(writer.toString());
        assertEquals("task18/mcp-parser/roundtrip-server-count", 2, roundTrip.mcpServers().size());
        assertEquals("task18/mcp-parser/roundtrip-stdio-type", McpTransportKind.STDIO, roundTrip.mcpServers().get("stdio-alpha").type());
        assertEquals("task18/mcp-parser/roundtrip-http-type", McpTransportKind.HTTP, roundTrip.mcpServers().get("http-beta").type());
        assertEquals("task18/mcp-parser/roundtrip-http-url", Optional.of("https://example.com/mcp"), roundTrip.mcpServers().get("http-beta").url());

        assertEquals(
                "task18/mcp-parser/null-writer",
                "MCP config writer is missing.",
                assertThrows("task18/mcp-parser/null-writer/throws", IllegalArgumentException.class,
                        () -> McpConfigParser.writeJson(null, config)).getMessage()
        );
    }

    private static McpConfig parse(String rawJson) {
        return McpConfigParser.parse(new StringReader(rawJson));
    }

    private static void assertContains(String assertionName, String haystack, String needle) {
        if (haystack != null && haystack.contains(needle)) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected to find: " + needle + " in: " + haystack);
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

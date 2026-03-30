package space.controlnet.mineagent.common.tools.mcp;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.tools.mcp.McpConfig;
import space.controlnet.mineagent.core.tools.mcp.McpConfigParser;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class McpConfigLoaderRegressionTest {
    @Test
    void task1_mcpConfigLoader_missingFile_writesDefaultJsonContract() throws Exception {
        Path tempRoot = Files.createTempDirectory("mineagent-mcp-loader-");
        Path configRoot = tempRoot.resolve("config");
        Path configPath = McpConfigLoader.resolveConfigPath(configRoot);

        McpConfig loaded = McpConfigLoader.load(configRoot);

        assertEquals("task1/mcp-loader/defaults-loaded", McpConfig.defaults(), loaded);
        assertTrue("task1/mcp-loader/config-created", Files.exists(configPath));
        assertEquals(
                "task1/mcp-loader/config-relative-path",
                Path.of("config", "mineagent", "mcp.json"),
                tempRoot.relativize(configPath)
        );

        String json = Files.readString(configPath, StandardCharsets.UTF_8);
        McpConfig reparsed = McpConfigParser.parse(new StringReader(json));
        assertEquals("task1/mcp-loader/defaults-roundtrip", McpConfig.defaults(), reparsed);
        assertContains("task1/mcp-loader/defaults-shape", json, "\"mcpServers\"");
    }

    @Test
    void task1_mcpConfigLoader_invalidJson_fallsBackToDefaultsWithoutTreatingDiskAsLoaded() throws Exception {
        Path tempRoot = Files.createTempDirectory("mineagent-mcp-loader-invalid-");
        Path configRoot = tempRoot.resolve("config");
        Path configPath = McpConfigLoader.resolveConfigPath(configRoot);
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, "{not-valid-json", StandardCharsets.UTF_8);

        McpConfigLoader.LoadResult result = McpConfigLoader.loadResult(configRoot);

        assertEquals("task1/mcp-loader/invalid-json-defaults", McpConfig.defaults(), result.config());
        assertEquals("task1/mcp-loader/invalid-json-path", configPath, result.path());
        assertTrue("task1/mcp-loader/invalid-json-not-loaded", !result.loadedFromDisk());
    }

    @Test
    void task1_mcpConfigLoader_invalidConfig_fallsBackToDefaultsAndKeepsOriginalFile() throws Exception {
        Path tempRoot = Files.createTempDirectory("mineagent-mcp-loader-invalid-config-");
        Path configRoot = tempRoot.resolve("config");
        Path configPath = McpConfigLoader.resolveConfigPath(configRoot);
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                {
                  "mcpServers": {
                    "Bad Alias": {
                      "type": "stdio"
                    }
                  }
                }
                """, StandardCharsets.UTF_8);

        McpConfigLoader.LoadResult result = McpConfigLoader.loadResult(configRoot);

        assertEquals("task1/mcp-loader/invalid-config-defaults", McpConfig.defaults(), result.config());
        assertTrue("task1/mcp-loader/invalid-config-not-loaded", !result.loadedFromDisk());
        assertContains("task1/mcp-loader/invalid-config-file-preserved",
                Files.readString(configPath, StandardCharsets.UTF_8), "Bad Alias");
    }

    private static void assertTrue(String assertionName, boolean value) {
        if (value) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
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

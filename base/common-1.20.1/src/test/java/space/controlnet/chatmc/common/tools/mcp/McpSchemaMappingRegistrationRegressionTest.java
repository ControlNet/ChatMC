package space.controlnet.chatmc.common.tools.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.common.tools.mcp.McpSchemaMapper.McpProjectedTool;
import space.controlnet.chatmc.common.tools.mcp.McpSchemaMapper.McpRemoteTool;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class McpSchemaMappingRegistrationRegressionTest {
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final String PROVIDER_ID = "task17-mcp-schema-mapping-provider";

    @BeforeEach
    void setUp() {
        cleanupRegistry();
    }

    @AfterEach
    void tearDown() {
        cleanupRegistry();
    }

    @Test
    void task17_configuredTool_isNamespacedAndMapped() {
        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject query = new JsonObject();
        query.addProperty("type", "string");
        query.addProperty("description", "Search query text");
        properties.add("query", query);

        JsonObject limit = new JsonObject();
        limit.addProperty("type", "integer");
        limit.addProperty("description", "Maximum number of results");
        properties.add("limit", limit);
        inputSchema.add("properties", properties);

        JsonArray required = new JsonArray();
        required.add("query");
        inputSchema.add("required", required);

        McpProjectedTool projection = McpSchemaMapper.project(
                "docs",
                new McpRemoteTool(
                        "search",
                        Optional.of("Search documentation pages."),
                        inputSchema,
                        Optional.of(true)
                )
        );

        AgentTool tool = projection.toolSpec();
        assertEquals("mcp.docs.search", projection.qualifiedToolName());
        assertEquals("mcp.docs.search", tool.name());
        assertEquals(Optional.of(true), projection.readOnlyHint());
        assertEquals("Search documentation pages.", tool.description());
        assertEquals(PRETTY_GSON.toJson(inputSchema), tool.argsSchema());
        assertEquals(List.of(
                "query: required string - Search query text",
                "limit: optional integer - Maximum number of results"
        ), tool.argsDescription());
        assertTrue(tool.resultSchemaOptional().isEmpty());
        assertEquals(List.of(), tool.resultDescriptionOptional());
        assertEquals(List.of(), tool.examplesOptional());
    }

    @Test
    void task17_missingReadonlyHint_doesNotBlockRegistration() {
        McpProjectedTool missingHint = McpSchemaMapper.project(
                "docs",
                new McpRemoteTool(
                        "fetch_page",
                        Optional.of("Fetch a single page."),
                        singlePropertySchema("path", true, "string", "Documentation path"),
                        Optional.empty()
                )
        );
        McpProjectedTool explicitFalse = McpSchemaMapper.project(
                "docs",
                new McpRemoteTool(
                        "list_pages",
                        Optional.of("List available pages."),
                        singlePropertySchema("prefix", false, "string", "Optional path prefix"),
                        Optional.of(false)
                )
        );

        ToolRegistry.registerOrReplace(PROVIDER_ID, new ProjectedToolProvider(missingHint, explicitFalse));

        assertTrue(missingHint.readOnlyHint().isEmpty());
        assertEquals(Optional.of(false), explicitFalse.readOnlyHint());
        assertEquals(ToolProvider.ExecutionAffinity.CALLING_THREAD,
                ToolRegistry.getExecutionAffinity("mcp.docs.fetch_page"));

        AgentTool fetchPage = ToolRegistry.getToolSpec("mcp.docs.fetch_page");
        AgentTool listPages = ToolRegistry.getToolSpec("mcp.docs.list_pages");
        assertNotNull(fetchPage);
        assertNotNull(listPages);
        assertEquals(List.of("mcp.docs.fetch_page", "mcp.docs.list_pages"), registeredDocsToolNames());
    }

    private static JsonObject singlePropertySchema(String name, boolean required, String type, String description) {
        JsonObject inputSchema = new JsonObject();
        inputSchema.addProperty("type", "object");

        JsonObject properties = new JsonObject();
        JsonObject property = new JsonObject();
        property.addProperty("type", type);
        property.addProperty("description", description);
        properties.add(name, property);
        inputSchema.add("properties", properties);

        if (required) {
            JsonArray requiredArray = new JsonArray();
            requiredArray.add(name);
            inputSchema.add("required", requiredArray);
        }

        return inputSchema;
    }

    private static List<String> registeredDocsToolNames() {
        List<String> names = new ArrayList<>();
        for (AgentTool tool : ToolRegistry.getToolSpecs()) {
            if (tool == null || tool.name() == null || !tool.name().startsWith("mcp.docs.")) {
                continue;
            }
            names.add(tool.name());
        }
        return names;
    }

    private static void cleanupRegistry() {
        ToolRegistry.unregister(PROVIDER_ID);
    }

    private static final class ProjectedToolProvider implements ToolProvider {
        private final List<McpProjectedTool> projections;

        private ProjectedToolProvider(McpProjectedTool... projections) {
            this.projections = List.of(projections);
        }

        @Override
        public List<AgentTool> specs() {
            return projections.stream()
                    .map(McpProjectedTool::toolSpec)
                    .toList();
        }

        @Override
        public ExecutionAffinity executionAffinity() {
            return ExecutionAffinity.CALLING_THREAD;
        }

        @Override
        public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
            if (call == null || call.toolName() == null) {
                return ToolOutcome.result(ToolResult.error("invalid_tool", "Missing tool"));
            }
            return ToolOutcome.result(ToolResult.ok("{\"tool\":\"" + call.toolName() + "\"}"));
        }
    }
}

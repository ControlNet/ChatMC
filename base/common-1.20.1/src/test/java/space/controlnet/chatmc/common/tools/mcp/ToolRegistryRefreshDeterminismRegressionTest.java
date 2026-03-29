package space.controlnet.chatmc.common.tools.mcp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import space.controlnet.chatmc.common.tools.ToolProvider;
import space.controlnet.chatmc.common.tools.ToolRegistry;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolPayload;
import space.controlnet.chatmc.core.tools.ToolRender;
import space.controlnet.chatmc.core.tools.ToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ToolRegistryRefreshDeterminismRegressionTest {
    private static final String TOOL_PREFIX = "task17.registry.";
    private static final String PROVIDER_ALPHA = "task17-provider-alpha";
    private static final String PROVIDER_BETA = "task17-provider-beta";

    @BeforeEach
    void setUp() {
        cleanupRegistry();
    }

    @AfterEach
    void tearDown() {
        cleanupRegistry();
    }

    @Test
    void task17_providerReplacement_removesStaleTools() {
        String alphaTool = TOOL_PREFIX + "alpha";
        String betaTool = TOOL_PREFIX + "beta";
        String sharedTool = TOOL_PREFIX + "shared";

        ToolRegistry.registerOrReplace(PROVIDER_ALPHA, new StaticToolProvider("alpha-v1", alphaTool, sharedTool));
        ToolRegistry.register(PROVIDER_BETA, new StaticToolProvider("beta", sharedTool));

        ToolRegistry.registerOrReplace(PROVIDER_ALPHA, new StaticToolProvider("alpha-v2", betaTool));

        assertOwnedToolNames(List.of(betaTool, sharedTool), ToolRegistry.getToolSpecs());
        assertNull(ToolRegistry.getToolSpec(alphaTool));
        assertNotNull(ToolRegistry.getToolSpec(betaTool));
        assertNotNull(ToolRegistry.getToolSpec(sharedTool));

        ToolOutcome replacementOutcome = ToolRegistry.executeTool(Optional.empty(), new ToolCall(betaTool, "{}"), true);
        assertSuccessPayload("task17/provider-replacement/beta-tool", replacementOutcome, "alpha-v2", betaTool);

        ToolOutcome sharedOutcome = ToolRegistry.executeTool(Optional.empty(), new ToolCall(sharedTool, "{}"), true);
        assertSuccessPayload("task17/provider-replacement/shared-tool", sharedOutcome, "beta", sharedTool);

        ToolOutcome staleOutcome = ToolRegistry.executeTool(Optional.empty(), new ToolCall(alphaTool, "{}"), true);
        assertUnknownTool("task17/provider-replacement/stale-tool", staleOutcome, alphaTool);
    }

    @Test
    void task17_sortedSnapshot_isStableAcrossReloads() {
        String alphaTool = TOOL_PREFIX + "alpha";
        String betaTool = TOOL_PREFIX + "beta";
        String gammaTool = TOOL_PREFIX + "gamma";
        String zetaTool = TOOL_PREFIX + "zeta";
        List<String> expected = List.of(alphaTool, betaTool, gammaTool, zetaTool);

        ToolRegistry.register(PROVIDER_ALPHA, new StaticToolProvider("alpha-v1", zetaTool, betaTool));
        ToolRegistry.register(PROVIDER_BETA, new StaticToolProvider("beta-v1", gammaTool, alphaTool));
        assertOwnedToolNames(expected, ToolRegistry.getToolSpecs());

        ToolRegistry.registerOrReplace(PROVIDER_BETA, new StaticToolProvider("beta-v2", alphaTool, gammaTool));
        assertOwnedToolNames(expected, ToolRegistry.getToolSpecs());

        ToolRegistry.unregister(PROVIDER_ALPHA);
        ToolRegistry.registerOrReplace(PROVIDER_ALPHA, new StaticToolProvider("alpha-v2", betaTool, zetaTool));
        assertOwnedToolNames(expected, ToolRegistry.getToolSpecs());
    }

    @Test
    void task17_snapshot_isImmutableAndDetachedFromLaterRefreshes() {
        String firstTool = TOOL_PREFIX + "first";
        String secondTool = TOOL_PREFIX + "second";

        ToolRegistry.registerOrReplace(PROVIDER_ALPHA, new StaticToolProvider("alpha-v1", firstTool));
        List<AgentTool> firstSnapshot = ToolRegistry.getToolSpecs();

        assertOwnedToolNames(List.of(firstTool), firstSnapshot);
        assertThrows(UnsupportedOperationException.class,
                () -> firstSnapshot.add(new StaticAgentTool(TOOL_PREFIX + "mutation")));

        ToolRegistry.registerOrReplace(PROVIDER_ALPHA, new StaticToolProvider("alpha-v2", secondTool));

        assertOwnedToolNames(List.of(firstTool), firstSnapshot);
        assertOwnedToolNames(List.of(secondTool), ToolRegistry.getToolSpecs());
    }

    private static void cleanupRegistry() {
        ToolRegistry.unregisterByPrefix(TOOL_PREFIX);
        ToolRegistry.unregister(PROVIDER_ALPHA);
        ToolRegistry.unregister(PROVIDER_BETA);
    }

    private static void assertOwnedToolNames(List<String> expectedNames, List<AgentTool> tools) {
        List<String> actualNames = new ArrayList<>();
        for (AgentTool tool : tools) {
            if (tool == null || tool.name() == null || !tool.name().startsWith(TOOL_PREFIX)) {
                continue;
            }
            actualNames.add(tool.name());
        }
        assertEquals(expectedNames, actualNames);
    }

    private static void assertSuccessPayload(String assertionName, ToolOutcome outcome, String expectedProvider,
                                             String expectedTool) {
        assertNotNull(outcome, assertionName + " -> outcome must not be null");
        assertNotNull(outcome.result(), assertionName + " -> result must not be null");
        assertTrue(outcome.result().success(), assertionName + " -> result must be successful");
        assertEquals(
                "{\"provider\":\"" + expectedProvider + "\",\"tool\":\"" + expectedTool + "\"}",
                outcome.result().payloadJson(),
                assertionName + " -> payload must match provider/tool marker"
        );
    }

    private static void assertUnknownTool(String assertionName, ToolOutcome outcome, String expectedToolName) {
        assertNotNull(outcome, assertionName + " -> outcome must not be null");
        assertNotNull(outcome.result(), assertionName + " -> result must not be null");
        assertTrue(!outcome.result().success(), assertionName + " -> result must be a failure");
        assertNotNull(outcome.result().error(), assertionName + " -> error must not be null");
        assertEquals("unknown_tool", outcome.result().error().code(), assertionName + " -> error code");
        assertEquals("Unknown tool: " + expectedToolName, outcome.result().error().message(),
                assertionName + " -> error message");
    }

    private record StaticAgentTool(String name) implements AgentTool {
        @Override
        public String description() {
            return "";
        }

        @Override
        public String argsSchema() {
            return "";
        }

        @Override
        public List<String> argsDescription() {
            return List.of();
        }

        @Override
        public String resultSchema() {
            return "";
        }

        @Override
        public List<String> resultDescription() {
            return List.of();
        }

        @Override
        public List<String> examples() {
            return List.of();
        }

        @Override
        public ToolRender render(ToolPayload payload) {
            return null;
        }
    }

    private static final class StaticToolProvider implements ToolProvider {
        private final String providerLabel;
        private final Map<String, AgentTool> toolsByName;

        private StaticToolProvider(String providerLabel, String... toolNames) {
            this.providerLabel = providerLabel;

            LinkedHashMap<String, AgentTool> tools = new LinkedHashMap<>();
            for (String toolName : toolNames) {
                tools.put(toolName, new StaticAgentTool(toolName));
            }
            this.toolsByName = Map.copyOf(tools);
        }

        @Override
        public List<AgentTool> specs() {
            return List.copyOf(toolsByName.values());
        }

        @Override
        public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
            if (call == null || call.toolName() == null || !toolsByName.containsKey(call.toolName())) {
                return ToolOutcome.result(ToolResult.error("unknown_tool", "Unknown tool: "
                        + (call == null ? "<missing>" : call.toolName())));
            }

            String payload = "{\"provider\":\"" + providerLabel + "\",\"tool\":\""
                    + call.toolName() + "\"}";
            return ToolOutcome.result(ToolResult.ok(payload));
        }
    }
}

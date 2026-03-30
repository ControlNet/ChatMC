package space.controlnet.mineagent.ae.common.tools;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import space.controlnet.mineagent.ae.core.proposal.AeProposalFactory;
import space.controlnet.mineagent.ae.core.terminal.AeTerminalContext;
import space.controlnet.mineagent.ae.core.tools.AeToolArgs;
import space.controlnet.mineagent.ae.core.tools.AeToolPolicy;
import space.controlnet.mineagent.common.tools.ToolOutputFormatter;
import space.controlnet.mineagent.common.tools.ToolProvider;
import space.controlnet.mineagent.core.policy.PolicyDecision;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.terminal.TerminalContext;
import space.controlnet.mineagent.core.tools.AgentTool;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolRender;
import space.controlnet.mineagent.core.tools.ToolResult;
import space.controlnet.mineagent.core.util.JsonSupport;

import java.util.List;
import java.util.Optional;

public final class AeToolProvider implements ToolProvider {
    private static final com.google.gson.Gson GSON = JsonSupport.GSON;

    private static final List<AgentTool> TOOLS = List.of(
            new SimpleTool(
                    "ae.list_items",
                    "List items in the connected ME network inventory.",
                    "{query, craftableOnly, limit, pageToken?}",
                    List.of(
                            "query: search string (empty for all items)",
                            "craftableOnly: true to filter to craftable-only entries",
                            "limit: max number of results to return",
                            "pageToken: optional pagination token"
                    ),
                    "{results: [AeEntry], nextPageToken?, error?}",
                    List.of(
                            "AeEntry fields: itemId, amount, craftable"
                    ),
                    List.of(
                            "{\"tool\":\"ae.list_items\",\"args\":{\"query\":\"fluix\",\"craftableOnly\":false,\"limit\":10}}"
                    ),
                    payload -> renderWithSummary("ui.mineagent.tool.ae.list_items",
                            List.of(formatQuery(payload)), payload)
            ),
            new SimpleTool(
                    "ae.list_craftables",
                    "List craftable items in the connected ME network.",
                    "{query, limit, pageToken?}",
                    List.of(
                            "query: search string (empty for all craftables)",
                            "limit: max number of results to return",
                            "pageToken: optional pagination token"
                    ),
                    "{results: [AeEntry], nextPageToken?, error?}",
                    List.of(
                            "AeEntry fields: itemId, amount, craftable"
                    ),
                    List.of(
                            "{\"tool\":\"ae.list_craftables\",\"args\":{\"query\":\"processor\",\"limit\":10}}"
                    ),
                    payload -> renderWithSummary("ui.mineagent.tool.ae.list_craftables",
                            List.of(formatQuerySuffix(payload)), payload)
            ),
            new SimpleTool(
                    "ae.simulate_craft",
                    "Simulate crafting an item to see missing materials.",
                    "{itemId, count}",
                    List.of(
                            "itemId: item id to craft",
                            "count: desired quantity"
                    ),
                    "{jobId, status, missingItems, error?}",
                    List.of(
                            "missingItems: list of {itemId, amount} required"
                    ),
                    List.of(
                            "{\"tool\":\"ae.simulate_craft\",\"args\":{\"itemId\":\"ae2:controller\",\"count\":1}}"
                    ),
                    payload -> renderWithSummary("ui.mineagent.tool.ae.simulate_craft",
                            List.of(formatCraftTarget(payload)), payload)
            ),
            new SimpleTool(
                    "ae.request_craft",
                    "Request a crafting job in the ME network.",
                    "{itemId, count, cpuName?}",
                    List.of(
                            "itemId: item id to craft",
                            "count: desired quantity",
                            "cpuName: optional crafting CPU name"
                    ),
                    "{jobId, status, error?}",
                    List.of(
                            "status: job status string"
                    ),
                    List.of(
                            "{\"tool\":\"ae.request_craft\",\"args\":{\"itemId\":\"ae2:controller\",\"count\":1}}"
                    ),
                    payload -> renderWithSummary("ui.mineagent.tool.ae.request_craft",
                            List.of(formatCraftTarget(payload)), payload)
            ),
            new SimpleTool(
                    "ae.job_status",
                    "Check the status of a crafting job.",
                    "{jobId}",
                    List.of(
                            "jobId: job identifier"
                    ),
                    "{jobId, status, missingItems, error?}",
                    List.of(
                            "missingItems: list of {itemId, amount} required"
                    ),
                    List.of(
                            "{\"tool\":\"ae.job_status\",\"args\":{\"jobId\":\"job-123\"}}"
                    ),
                    payload -> renderWithSummary("ui.mineagent.tool.ae.job_status",
                            List.of(formatArg(payload, "jobId")), payload)
            ),
            new SimpleTool(
                    "ae.job_cancel",
                    "Cancel a crafting job.",
                    "{jobId}",
                    List.of(
                            "jobId: job identifier"
                    ),
                    "{jobId, status, missingItems, error?}",
                    List.of(
                            "missingItems: list of {itemId, amount} required"
                    ),
                    List.of(
                            "{\"tool\":\"ae.job_cancel\",\"args\":{\"jobId\":\"job-123\"}}"
                    ),
                    payload -> renderWithSummary("ui.mineagent.tool.ae.job_cancel",
                            List.of(formatArg(payload, "jobId")), payload)
            )
    );

    @Override
    public List<AgentTool> specs() {
        return TOOLS;
    }

    @Override
    public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        if (call == null || call.toolName() == null) {
            return ToolOutcome.result(ToolResult.error("invalid_tool", "Missing tool"));
        }

        RiskLevel risk = AeToolPolicy.classifyRisk(call.toolName());
        if (!approved) {
            PolicyDecision decision = AeToolPolicy.policyFor(risk);
            if (decision == PolicyDecision.REQUIRE_APPROVAL) {
                return ToolOutcome.proposal(AeProposalFactory.build(risk, call));
            }
            if (decision == PolicyDecision.DENY) {
                return ToolOutcome.result(ToolResult.error("denied", "Policy denied tool"));
            }
        }

        AeTerminalContext ctx = terminal.filter(t -> t instanceof AeTerminalContext)
                .map(t -> (AeTerminalContext) t)
                .orElse(null);

        try {
            ToolResult result = switch (call.toolName()) {
                case "ae.list_items" -> handleAeListItems(ctx, call);
                case "ae.list_craftables" -> handleAeListCraftables(ctx, call);
                case "ae.simulate_craft" -> handleAeSimulate(ctx, call);
                case "ae.request_craft" -> handleAeRequest(ctx, call);
                case "ae.job_status" -> handleAeJobStatus(ctx, call);
                case "ae.job_cancel" -> handleAeJobCancel(ctx, call);
                default -> ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName());
            };
            return ToolOutcome.result(result);
        } catch (IllegalArgumentException illegalArgumentException) {
            return ToolOutcome.result(ToolResult.error("invalid_args",
                    requireMessage(illegalArgumentException, "Missing arguments")));
        }
    }

    private static ToolResult handleAeListItems(AeTerminalContext terminal, ToolCall call) {
        var args = parseArgs(call.argsJson(), AeToolArgs.AeListArgs.class);
        if (args == null) {
            throw new IllegalArgumentException("Missing arguments");
        }
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.listItems(args.query(), args.craftableOnly(), args.limit(), args.pageToken());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAeListCraftables(AeTerminalContext terminal, ToolCall call) {
        var args = parseArgs(call.argsJson(), AeToolArgs.AeListArgs.class);
        if (args == null) {
            throw new IllegalArgumentException("Missing arguments");
        }
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.listCraftables(args.query(), args.limit(), args.pageToken());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAeSimulate(AeTerminalContext terminal, ToolCall call) {
        var args = parseArgs(call.argsJson(), AeToolArgs.AeCraftArgs.class);
        if (args == null) {
            throw new IllegalArgumentException("Missing arguments");
        }
        if (args.itemId() == null || args.itemId().isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (!AeToolPolicy.isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + AeToolPolicy.getMaxCraftCount());
        }
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.simulateCraft(args.itemId(), args.count());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAeRequest(AeTerminalContext terminal, ToolCall call) {
        var args = parseArgs(call.argsJson(), AeToolArgs.AeCraftArgs.class);
        if (args == null) {
            throw new IllegalArgumentException("Missing arguments");
        }
        if (args.itemId() == null || args.itemId().isBlank()) {
            throw new IllegalArgumentException("itemId is required");
        }
        if (!AeToolPolicy.isValidCraftCount(args.count())) {
            return ToolResult.error("invalid_count", "Count must be between 1 and " + AeToolPolicy.getMaxCraftCount());
        }
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.requestCraft(args.itemId(), args.count(), args.cpuName());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAeJobStatus(AeTerminalContext terminal, ToolCall call) {
        var args = parseArgs(call.argsJson(), AeToolArgs.AeJobArgs.class);
        if (args == null) {
            throw new IllegalArgumentException("Missing arguments");
        }
        if (args.jobId() == null || args.jobId().isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.jobStatus(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleAeJobCancel(AeTerminalContext terminal, ToolCall call) {
        var args = parseArgs(call.argsJson(), AeToolArgs.AeJobArgs.class);
        if (args == null) {
            throw new IllegalArgumentException("Missing arguments");
        }
        if (args.jobId() == null || args.jobId().isBlank()) {
            throw new IllegalArgumentException("jobId is required");
        }
        if (terminal == null) {
            return ToolResult.error("no_terminal", "No AI Terminal is open");
        }
        var result = terminal.cancelJob(args.jobId());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static <T> T parseArgs(String argsJson, Class<T> type) {
        if (argsJson == null || argsJson.isBlank()) {
            return null;
        }
        try {
            return GSON.fromJson(argsJson, type);
        } catch (RuntimeException runtimeException) {
            throw new IllegalArgumentException("Malformed JSON arguments", runtimeException);
        }
    }

    private static String requireMessage(Throwable throwable, String fallback) {
        if (throwable == null || throwable.getMessage() == null || throwable.getMessage().isBlank()) {
            return fallback;
        }
        return throwable.getMessage();
    }

    private interface Renderer {
        ToolRender render(space.controlnet.mineagent.core.tools.ToolPayload payload);
    }

    private static final class SimpleTool implements AgentTool {
        private final String name;
        private final String description;
        private final String argsSchema;
        private final List<String> argsDescription;
        private final String resultSchema;
        private final List<String> resultDescription;
        private final List<String> examples;
        private final Renderer renderer;

        private SimpleTool(
                String name,
                String description,
                String argsSchema,
                List<String> argsDescription,
                String resultSchema,
                List<String> resultDescription,
                List<String> examples,
                Renderer renderer
        ) {
            this.name = name;
            this.description = description;
            this.argsSchema = argsSchema;
            this.argsDescription = List.copyOf(argsDescription);
            this.resultSchema = resultSchema;
            this.resultDescription = List.copyOf(resultDescription);
            this.examples = List.copyOf(examples);
            this.renderer = renderer;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String description() {
            return description;
        }

        @Override
        public String argsSchema() {
            return argsSchema;
        }

        @Override
        public List<String> argsDescription() {
            return argsDescription;
        }

        @Override
        public String resultSchema() {
            return resultSchema;
        }

        @Override
        public List<String> resultDescription() {
            return resultDescription;
        }

        @Override
        public List<String> examples() {
            return examples;
        }

        @Override
        public ToolRender render(space.controlnet.mineagent.core.tools.ToolPayload payload) {
            return renderer.render(payload);
        }
    }

    private static ToolRender renderWithSummary(String key, List<String> parts, space.controlnet.mineagent.core.tools.ToolPayload payload) {
        String summary = Component.translatable(key, parts.toArray()).getString();
        List<String> lines = ToolOutputFormatter.formatLines(payload == null ? null : payload.outputJson());
        return new ToolRender(summary, parts, lines, payload == null ? null : payload.error());
    }

    private static String formatQuery(space.controlnet.mineagent.core.tools.ToolPayload payload) {
        JsonObject args = payload == null ? null : ToolOutputFormatter.parseJsonObject(payload.argsJson());
        if (args == null) {
            return "";
        }
        String query = args.has("query") && args.get("query").isJsonPrimitive() ? args.get("query").getAsString() : "";
        if (query == null || query.isBlank()) {
            return Component.translatable("ui.mineagent.tool.query.empty").getString();
        }
        return query;
    }

    private static String formatQuerySuffix(space.controlnet.mineagent.core.tools.ToolPayload payload) {
        JsonObject args = payload == null ? null : ToolOutputFormatter.parseJsonObject(payload.argsJson());
        if (args == null) {
            return "";
        }
        String query = args.has("query") && args.get("query").isJsonPrimitive() ? args.get("query").getAsString() : "";
        if (query == null || query.isBlank()) {
            return "";
        }
        return " (" + query + ")";
    }

    private static String formatCraftTarget(space.controlnet.mineagent.core.tools.ToolPayload payload) {
        JsonObject args = payload == null ? null : ToolOutputFormatter.parseJsonObject(payload.argsJson());
        if (args == null) {
            return "unknown";
        }
        String itemId = args.has("itemId") && args.get("itemId").isJsonPrimitive()
                ? args.get("itemId").getAsString()
                : null;
        long count = args.has("count") && args.get("count").isJsonPrimitive()
                ? args.get("count").getAsLong()
                : 1L;
        String prefix = count > 1 ? (count + "x ") : "";
        return prefix + ToolOutputFormatter.formatItemTag(itemId);
    }

    private static String formatArg(space.controlnet.mineagent.core.tools.ToolPayload payload, String key) {
        JsonObject args = payload == null ? null : ToolOutputFormatter.parseJsonObject(payload.argsJson());
        if (args == null) {
            return "?";
        }
        if (!args.has(key) || !args.get(key).isJsonPrimitive()) {
            return "?";
        }
        return args.get(key).getAsString();
    }
}

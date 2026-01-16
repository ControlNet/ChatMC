package space.controlnet.chatae.common.tools;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import space.controlnet.chatae.core.tools.AgentTool;
import space.controlnet.chatae.core.tools.ToolPayload;
import space.controlnet.chatae.core.tools.ToolRender;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AgentToolRegistry {
    private static final List<AgentTool> TOOLS = List.of(
            new SimpleTool(
                    "mc.find_recipes",
                    "Find recipes that craft the requested item (JEI R key behavior).",
                    "{itemId, pageToken?, limit}",
                    List.of(
                            "itemId: required item id to craft, for example \"minecraft:chest\"",
                            "pageToken: optional pagination token",
                            "limit: max number of results to return"
                    ),
                    "{results: [RecipeSummary], nextPageToken?}",
                    List.of(
                            "RecipeSummary fields: recipeId, recipeType, outputItemId, outputCount, ingredientItemIds"
                    ),
                    List.of(
                            "{\"tool\":\"mc.find_recipes\",\"args\":{\"itemId\":\"minecraft:chest\",\"limit\":5}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.mc.find_recipes",
                            List.of(formatItemTarget(payload)), payload)
            ),
            new SimpleTool(
                    "mc.find_usage",
                    "Find recipes that use the requested item as an ingredient (JEI U key behavior).",
                    "{itemId, pageToken?, limit}",
                    List.of(
                            "itemId: required ingredient item id, for example \"minecraft:iron_ingot\"",
                            "pageToken: optional pagination token",
                            "limit: max number of results to return"
                    ),
                    "{results: [RecipeSummary], nextPageToken?}",
                    List.of(
                            "RecipeSummary fields: recipeId, recipeType, outputItemId, outputCount, ingredientItemIds"
                    ),
                    List.of(
                            "{\"tool\":\"mc.find_usage\",\"args\":{\"itemId\":\"minecraft:iron_ingot\",\"limit\":5}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.mc.find_usage",
                            List.of(formatItemTarget(payload)), payload)
            ),
            new SimpleTool(
                    "ae2.list_items",
                    "List items in the connected ME network inventory.",
                    "{query, craftableOnly, limit, pageToken?}",
                    List.of(
                            "query: search string (empty for all items)",
                            "craftableOnly: true to filter to craftable-only entries",
                            "limit: max number of results to return",
                            "pageToken: optional pagination token"
                    ),
                    "{results: [Ae2Entry], nextPageToken?, error?}",
                    List.of(
                            "Ae2Entry fields: itemId, amount, craftable"
                    ),
                    List.of(
                            "{\"tool\":\"ae2.list_items\",\"args\":{\"query\":\"fluix\",\"craftableOnly\":false,\"limit\":10}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.ae2.list_items",
                            List.of(formatQuery(payload)), payload)
            ),
            new SimpleTool(
                    "ae2.list_craftables",
                    "List craftable items in the connected ME network.",
                    "{query, limit, pageToken?}",
                    List.of(
                            "query: search string (empty for all craftables)",
                            "limit: max number of results to return",
                            "pageToken: optional pagination token"
                    ),
                    "{results: [Ae2Entry], nextPageToken?, error?}",
                    List.of(
                            "Ae2Entry fields: itemId, amount, craftable"
                    ),
                    List.of(
                            "{\"tool\":\"ae2.list_craftables\",\"args\":{\"query\":\"processor\",\"limit\":10}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.ae2.list_craftables",
                            List.of(formatQuerySuffix(payload)), payload)
            ),
            new SimpleTool(
                    "ae2.simulate_craft",
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
                            "{\"tool\":\"ae2.simulate_craft\",\"args\":{\"itemId\":\"ae2:controller\",\"count\":1}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.ae2.simulate_craft",
                            List.of(formatCraftTarget(payload)), payload)
            ),
            new SimpleTool(
                    "ae2.request_craft",
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
                            "{\"tool\":\"ae2.request_craft\",\"args\":{\"itemId\":\"ae2:controller\",\"count\":1}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.ae2.request_craft",
                            List.of(formatCraftTarget(payload)), payload)
            ),
            new SimpleTool(
                    "ae2.job_status",
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
                            "{\"tool\":\"ae2.job_status\",\"args\":{\"jobId\":\"job-123\"}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.ae2.job_status",
                            List.of(formatArg(payload, "jobId")), payload)
            ),
            new SimpleTool(
                    "ae2.job_cancel",
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
                            "{\"tool\":\"ae2.job_cancel\",\"args\":{\"jobId\":\"job-123\"}}"
                    ),
                    payload -> renderWithSummary("ui.chatae.tool.ae2.job_cancel",
                            List.of(formatArg(payload, "jobId")), payload)
            ),
            new SimpleTool(
                    "response",
                    "Respond to the user and end the agent loop.",
                    "{message}",
                    List.of(
                            "message: final response text for the user"
                    ),
                    "{}",
                    List.of(
                            "No tool output. This ends the loop."
                    ),
                    List.of(
                            "{\"tool\":\"response\",\"args\":{\"message\":\"Here is the answer.\"}}"
                    ),
                    payload -> new ToolRender(null, List.of(), List.of(), payload == null ? null : payload.error())
            )
    );

    private static final Map<String, AgentTool> BY_NAME = buildIndex();

    private AgentToolRegistry() {
    }

    public static List<AgentTool> all() {
        return TOOLS;
    }

    public static AgentTool get(String name) {
        if (name == null) {
            return null;
        }
        return BY_NAME.get(name);
    }

    private static Map<String, AgentTool> buildIndex() {
        Map<String, AgentTool> map = new HashMap<>();
        for (AgentTool tool : TOOLS) {
            map.put(tool.name(), tool);
        }
        return Collections.unmodifiableMap(map);
    }

    private interface Renderer {
        ToolRender render(ToolPayload payload);
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
        public ToolRender render(ToolPayload payload) {
            if (renderer == null) {
                return new ToolRender(null, List.of(), List.of(), payload == null ? null : payload.error());
            }
            return renderer.render(payload);
        }
    }

    private static ToolRender renderWithSummary(String summaryKey, List<String> summaryArgs, ToolPayload payload) {
        List<String> lines = payload == null
                ? List.of()
                : ToolOutputFormatter.formatLines(payload.outputJson());
        String error = payload == null ? null : payload.error();
        return new ToolRender(summaryKey, summaryArgs, lines, error);
    }

    private static String formatItemTarget(ToolPayload payload) {
        JsonObject args = parseArgs(payload);
        String itemId = getString(args, "itemId");
        if (itemId == null || itemId.isBlank()) {
            return emptyLabel();
        }
        return ToolOutputFormatter.formatItemTag(itemId);
    }

    private static String formatCraftTarget(ToolPayload payload) {
        JsonObject args = parseArgs(payload);
        long count = getLong(args, "count", 1);
        String prefix = count > 1 ? (count + "x ") : "";
        String itemId = getString(args, "itemId");
        if (itemId == null || itemId.isBlank()) {
            return prefix + emptyLabel();
        }
        return prefix + ToolOutputFormatter.formatItemTag(itemId);
    }

    private static String formatQuery(ToolPayload payload) {
        JsonObject args = parseArgs(payload);
        String query = getString(args, "query");
        if (query == null || query.isBlank()) {
            return emptyLabel();
        }
        return query;
    }

    private static String formatQuerySuffix(ToolPayload payload) {
        JsonObject args = parseArgs(payload);
        String query = getString(args, "query");
        if (query == null || query.isBlank()) {
            return "";
        }
        return " (" + query + ")";
    }

    private static String formatArg(ToolPayload payload, String key) {
        JsonObject args = parseArgs(payload);
        String value = getString(args, key);
        if (value == null || value.isBlank()) {
            return emptyLabel();
        }
        return value;
    }

    private static String emptyLabel() {
        return Component.translatable("ui.chatae.tool.query.empty").getString();
    }

    private static JsonObject parseArgs(ToolPayload payload) {
        if (payload == null || payload.argsJson() == null || payload.argsJson().isBlank()) {
            return null;
        }
        try {
            return JsonParser.parseString(payload.argsJson()).getAsJsonObject();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getString(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static long getLong(JsonObject obj, String key, long fallback) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsLong();
        } catch (Exception ignored) {
            return fallback;
        }
    }
}

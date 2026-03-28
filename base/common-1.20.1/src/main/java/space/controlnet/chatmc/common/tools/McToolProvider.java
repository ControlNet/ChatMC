package space.controlnet.chatmc.common.tools;

import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import space.controlnet.chatmc.common.ChatMC;
import space.controlnet.chatmc.core.recipes.RecipeSearchResult;
import space.controlnet.chatmc.core.terminal.TerminalContext;
import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.ToolArgs;
import space.controlnet.chatmc.core.tools.ToolCall;
import space.controlnet.chatmc.core.tools.ToolOutcome;
import space.controlnet.chatmc.core.tools.ToolRender;
import space.controlnet.chatmc.core.tools.ToolResult;
import space.controlnet.chatmc.core.util.JsonSupport;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default provider for vanilla mc.* tools.
 */
public final class McToolProvider implements ToolProvider {
    private static final com.google.gson.Gson GSON = JsonSupport.GSON;

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
                    payload -> renderWithSummary("ui.chatmc.tool.mc.find_recipes",
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
                    payload -> renderWithSummary("ui.chatmc.tool.mc.find_usage",
                            List.of(formatItemTarget(payload)), payload)
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

    @Override
    public List<AgentTool> specs() {
        return TOOLS;
    }

    @Override
    public ToolOutcome execute(Optional<TerminalContext> terminal, ToolCall call, boolean approved) {
        if (call == null || call.toolName() == null) {
            return ToolOutcome.result(ToolResult.error("invalid_tool", "Missing tool"));
        }
        ToolResult result = switch (call.toolName()) {
            case "mc.find_recipes" -> handleMcFindRecipes(call);
            case "mc.find_usage" -> handleMcFindUsage(call);
            default -> ToolResult.error("unknown_tool", "Unknown tool: " + call.toolName());
        };
        return ToolOutcome.result(result);
    }

    private static ToolResult handleMcFindRecipes(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.McFindRecipesArgs.class);
        if (args == null || args.itemId() == null || args.itemId().isBlank()) {
            return ToolResult.error("invalid_args", "Missing itemId");
        }
        if (!isValidItemId(args.itemId())) {
            return ToolResult.error("invalid_item_id", "Item id not found: " + args.itemId());
        }
        if (!ChatMC.RECIPE_INDEX.isReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        RecipeSearchResult result = ChatMC.RECIPE_INDEX.findByOutput(args.itemId(), Optional.ofNullable(args.pageToken()), args.limit());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static ToolResult handleMcFindUsage(ToolCall call) {
        var args = GSON.fromJson(call.argsJson(), ToolArgs.McFindUsageArgs.class);
        if (args == null || args.itemId() == null || args.itemId().isBlank()) {
            return ToolResult.error("invalid_args", "Missing itemId");
        }
        if (!isValidItemId(args.itemId())) {
            return ToolResult.error("invalid_item_id", "Item id not found: " + args.itemId());
        }
        if (!ChatMC.RECIPE_INDEX.isReady()) {
            return ToolResult.error("index_not_ready", "Recipe index not ready");
        }
        RecipeSearchResult result = ChatMC.RECIPE_INDEX.findByIngredient(args.itemId(), Optional.ofNullable(args.pageToken()), args.limit());
        return ToolResult.ok(GSON.toJson(result));
    }

    private static boolean isValidItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return false;
        }
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) {
            return false;
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(id);
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
        ToolRender render(space.controlnet.chatmc.core.tools.ToolPayload payload);
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
        public ToolRender render(space.controlnet.chatmc.core.tools.ToolPayload payload) {
            return renderer.render(payload);
        }
    }

    private static ToolRender renderWithSummary(String key, List<String> parts, space.controlnet.chatmc.core.tools.ToolPayload payload) {
        String summary = Component.translatable(key, parts.toArray()).getString();
        List<String> lines = ToolOutputFormatter.formatLines(payload == null ? null : payload.outputJson());
        return new ToolRender(summary, parts, lines, payload == null ? null : payload.error());
    }

    private static String formatItemTarget(space.controlnet.chatmc.core.tools.ToolPayload payload) {
        JsonObject args = payload == null ? null : ToolOutputFormatter.parseJsonObject(payload.argsJson());
        if (args == null) {
            return "unknown";
        }
        String itemId = null;
        if (args.has("itemId") && args.get("itemId").isJsonPrimitive()) {
            itemId = args.get("itemId").getAsString();
        }
        return ToolOutputFormatter.formatItemTag(itemId);
    }
}

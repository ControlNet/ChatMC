package space.controlnet.chatae.common.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import space.controlnet.chatae.core.tools.ToolPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared tool output formatter for UI display.
 */
public final class ToolOutputFormatter {
    private static final int TOOL_RESULT_MAX = 8;

    private ToolOutputFormatter() {
    }

    public static String formatItemTag(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "unknown";
        }
        ResourceLocation id = ResourceLocation.tryParse(itemId);
        if (id == null) {
            return itemId;
        }
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == null || item == net.minecraft.world.item.Items.AIR) {
            return itemId;
        }
        String name = new ItemStack(item).getHoverName().getString().replace("\"", "'");
        return "<item id=\"" + id + "\" display_name=\"" + name + "\">";
    }

    public static String formatItemList(JsonArray items, int limit) {
        if (items == null || items.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        int shown = Math.min(items.size(), Math.max(1, limit));
        for (int i = 0; i < shown; i++) {
            JsonElement el = items.get(i);
            if (el != null && el.isJsonPrimitive()) {
                parts.add(formatItemTag(el.getAsString()));
            }
        }
        if (items.size() > shown) {
            parts.add("+" + (items.size() - shown) + " more");
        }
        return String.join(", ", parts);
    }

    public static List<String> formatLines(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return List.of();
        }
        if ("null".equals(text)) {
            return List.of("No result.");
        }
        if (!text.startsWith("{") && !text.startsWith("[")) {
            return List.of(raw);
        }
        try {
            JsonElement element = JsonParser.parseString(text);
            if (!element.isJsonObject()) {
                return List.of(raw);
            }
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("results") && obj.get("results").isJsonArray()) {
                JsonArray results = obj.getAsJsonArray("results");
                if (isRecipeResults(results)) {
                    return formatRecipeSearch(results, getString(obj, "nextPageToken"));
                }
                if (isAe2ListResults(results)) {
                    return formatAe2List(results, getString(obj, "nextPageToken"), getString(obj, "error"));
                }
            }
            if (obj.has("recipeId") || obj.has("outputItemId")) {
                return formatRecipeSummary(obj);
            }
            if (obj.has("jobId")) {
                return formatJobStatus(obj);
            }
            if (obj.has("status") || obj.has("error")) {
                List<String> lines = new ArrayList<>();
                String status = getString(obj, "status");
                if (status != null && !status.isBlank()) {
                    lines.add("Status: " + status);
                }
                String error = getString(obj, "error");
                if (error != null && !error.isBlank()) {
                    lines.add("Error: " + error);
                }
                if (!lines.isEmpty()) {
                    return lines;
                }
            }
        } catch (Exception ignored) {
            return List.of(raw);
        }
        return List.of(raw);
    }

    public static String formatQuery(JsonObject args) {
        String query = getString(args, "query");
        if (query == null || query.isBlank()) {
            return "all items";
        }
        return query;
    }

    public static String formatQuerySuffix(JsonObject args) {
        String query = getString(args, "query");
        if (query == null || query.isBlank()) {
            return "";
        }
        return " (" + query + ")";
    }

    public static String formatCraftTarget(JsonObject args) {
        String itemId = getString(args, "itemId");
        long count = getLong(args, "count", 1);
        String prefix = count > 1 ? (count + "x ") : "";
        return prefix + formatItemTag(itemId);
    }

    public static String formatArg(JsonObject args, String key) {
        String value = getString(args, key);
        return value == null ? "?" : value;
    }

    public static JsonObject parseJsonObject(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(raw);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static ToolPayload parsePayload(String raw) {
        return space.controlnet.chatae.core.tools.ToolMessagePayload.parse(raw);
    }

    private static List<String> formatRecipeSearch(JsonArray results, String nextPageToken) {
        List<String> lines = new ArrayList<>();
        int total = results.size();
        if (total == 0) {
            lines.add("No recipes found.");
        } else {
            lines.add("Recipes (" + total + "):");
            int shown = Math.min(total, TOOL_RESULT_MAX);
            for (int i = 0; i < shown; i++) {
                JsonObject obj = safeObject(results.get(i));
                if (obj == null) {
                    continue;
                }
                String itemId = getString(obj, "outputItemId");
                int count = getInt(obj, "outputCount", 1);
                String type = getString(obj, "recipeType");
                String label = (count > 1 ? count + "x " : "") + formatItemTag(itemId);
                if (type != null && !type.isBlank()) {
                    label += " [" + type + "]";
                }
                lines.add("• " + label);
            }
            if (total > shown) {
                lines.add("• +" + (total - shown) + " more");
            }
        }
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            lines.add("Next page: " + nextPageToken);
        }
        return lines;
    }

    private static List<String> formatRecipeSummary(JsonObject obj) {
        List<String> lines = new ArrayList<>();
        String outputItem = getString(obj, "outputItemId");
        int count = getInt(obj, "outputCount", 1);
        String type = getString(obj, "recipeType");
        String recipeId = getString(obj, "recipeId");
        String header = "Recipe: " + (count > 1 ? count + "x " : "") + formatItemTag(outputItem);
        lines.add(header);
        if (type != null && !type.isBlank()) {
            lines.add("Type: " + type);
        }
        if (recipeId != null && !recipeId.isBlank()) {
            lines.add("Id: " + recipeId);
        }
        JsonArray ingredients = obj.getAsJsonArray("ingredientItemIds");
        if (ingredients != null) {
            lines.add("Ingredients: " + formatItemList(ingredients, 6));
        }
        return lines;
    }

    private static List<String> formatAe2List(JsonArray results, String nextPageToken, String error) {
        List<String> lines = new ArrayList<>();
        int total = results.size();
        if (total == 0) {
            lines.add("No items found.");
        } else {
            lines.add("Items (" + total + "):");
            int shown = Math.min(total, TOOL_RESULT_MAX);
            for (int i = 0; i < shown; i++) {
                JsonObject obj = safeObject(results.get(i));
                if (obj == null) {
                    continue;
                }
                String itemId = getString(obj, "itemId");
                long amount = getLong(obj, "amount", 0);
                boolean craftable = getBoolean(obj, "craftable");
                String label = formatItemTag(itemId) + " — " + amount;
                if (craftable) {
                    label += " (craftable)";
                }
                lines.add("• " + label);
            }
            if (total > shown) {
                lines.add("• +" + (total - shown) + " more");
            }
        }
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            lines.add("Next page: " + nextPageToken);
        }
        if (error != null && !error.isBlank()) {
            lines.add("Error: " + error);
        }
        return lines;
    }

    private static List<String> formatJobStatus(JsonObject obj) {
        List<String> lines = new ArrayList<>();
        String jobId = getString(obj, "jobId");
        String status = getString(obj, "status");
        String header = "Job " + (jobId == null ? "" : jobId);
        if (status != null && !status.isBlank()) {
            header += " — " + status;
        }
        lines.add(header.trim());

        JsonArray missing = obj.getAsJsonArray("missingItems");
        if (missing != null && missing.size() > 0) {
            lines.add("Missing:");
            int shown = Math.min(missing.size(), TOOL_RESULT_MAX);
            for (int i = 0; i < shown; i++) {
                JsonObject item = safeObject(missing.get(i));
                if (item == null) {
                    continue;
                }
                String itemId = getString(item, "itemId");
                long amount = getLong(item, "amount", 0);
                lines.add("• " + amount + "x " + formatItemTag(itemId));
            }
            if (missing.size() > shown) {
                lines.add("• +" + (missing.size() - shown) + " more");
            }
        }

        String error = getString(obj, "error");
        if (error != null && !error.isBlank()) {
            lines.add("Error: " + error);
        }
        return lines;
    }

    private static boolean isRecipeResults(JsonArray results) {
        JsonObject first = firstObject(results);
        return first != null && (first.has("recipeId") || first.has("outputItemId"));
    }

    private static boolean isAe2ListResults(JsonArray results) {
        JsonObject first = firstObject(results);
        return first != null && first.has("itemId") && first.has("amount");
    }

    private static JsonObject firstObject(JsonArray array) {
        if (array == null || array.isEmpty()) {
            return null;
        }
        return safeObject(array.get(0));
    }

    private static JsonObject safeObject(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        return element.getAsJsonObject();
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

    private static int getInt(JsonObject obj, String key, int fallback) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
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

    private static boolean getBoolean(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return false;
        }
        try {
            return obj.get(key).getAsBoolean();
        } catch (Exception ignored) {
            return false;
        }
    }
}

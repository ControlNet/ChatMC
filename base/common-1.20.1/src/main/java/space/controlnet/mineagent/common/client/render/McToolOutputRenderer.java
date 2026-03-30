package space.controlnet.mineagent.common.client.render;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import space.controlnet.mineagent.common.tools.ToolOutputFormatter;

import java.util.ArrayList;
import java.util.List;

public final class McToolOutputRenderer implements ToolOutputRenderer {
    private static final int TOOL_RESULT_MAX = 8;

    @Override
    public boolean canRender(JsonObject output) {
        if (output == null) {
            return false;
        }
        if (output.has("results") && output.get("results").isJsonArray()) {
            JsonArray results = output.getAsJsonArray("results");
            return isRecipeResults(results);
        }
        return output.has("recipeId") || output.has("outputItemId");
    }

    @Override
    public List<String> render(JsonObject output) {
        if (output == null) {
            return null;
        }
        if (output.has("results") && output.get("results").isJsonArray()) {
            JsonArray results = output.getAsJsonArray("results");
            if (isRecipeResults(results)) {
                return formatRecipeSearch(results, getString(output, "nextPageToken"));
            }
        }
        if (output.has("recipeId") || output.has("outputItemId")) {
            return formatRecipeSummary(output);
        }
        return null;
    }

    private boolean isRecipeResults(JsonArray results) {
        JsonObject first = firstObject(results);
        return first != null && (first.has("recipeId") || first.has("outputItemId"));
    }

    private List<String> formatRecipeSearch(JsonArray results, String nextPageToken) {
        List<String> lines = new ArrayList<>();
        int total = results.size();
        if (total == 0) {
            lines.add("No recipes found.");
        } else {
            lines.add("Found " + total + " recipes.");
        }
        if (nextPageToken != null && !nextPageToken.isBlank()) {
            lines.add("Next page: " + nextPageToken);
        }
        return lines;
    }

    private List<String> formatRecipeSummary(JsonObject obj) {
        List<String> lines = new ArrayList<>();
        String outputItem = getString(obj, "outputItemId");
        int count = getInt(obj, "outputCount", 1);
        String type = getString(obj, "recipeType");
        String recipeId = getString(obj, "recipeId");
        String header = "Recipe: " + (count > 1 ? count + "x " : "") + ToolOutputFormatter.formatItemTag(outputItem);
        lines.add(header);
        if (type != null && !type.isBlank()) {
            lines.add("Type: " + type);
        }
        if (recipeId != null && !recipeId.isBlank()) {
            lines.add("Id: " + recipeId);
        }
        JsonArray ingredients = obj.getAsJsonArray("ingredientItemIds");
        if (ingredients != null) {
            lines.add("Ingredients: " + ToolOutputFormatter.formatItemList(ingredients, 6));
        }
        return lines;
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
}

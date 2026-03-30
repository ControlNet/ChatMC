package space.controlnet.mineagent.common.tools;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import space.controlnet.mineagent.common.client.render.ToolOutputRendererRegistry;
import space.controlnet.mineagent.common.tools.mcp.McpFallbackRenderer;
import space.controlnet.mineagent.core.tools.ToolPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared tool output formatter for UI display.
 */
public final class ToolOutputFormatter {

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

            List<String> rendererResult = ToolOutputRendererRegistry.tryRender(obj);
            if (rendererResult != null) {
                return rendererResult;
            }

            List<String> mcpFallbackLines = McpFallbackRenderer.renderLines(obj);
            if (mcpFallbackLines != null) {
                return mcpFallbackLines;
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
        return space.controlnet.mineagent.core.tools.ToolMessagePayload.parse(raw);
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

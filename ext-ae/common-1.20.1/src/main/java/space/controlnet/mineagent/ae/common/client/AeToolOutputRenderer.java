package space.controlnet.mineagent.ae.common.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import space.controlnet.mineagent.common.client.render.ToolOutputRenderer;
import space.controlnet.mineagent.common.tools.ToolOutputFormatter;

import java.util.ArrayList;
import java.util.List;

public final class AeToolOutputRenderer implements ToolOutputRenderer {
    private static final int TOOL_RESULT_MAX = 8;

    @Override
    public boolean canRender(JsonObject output) {
        if (output == null) {
            return false;
        }
        if (output.has("results") && output.get("results").isJsonArray()) {
            JsonArray results = output.getAsJsonArray("results");
            return isAeListResults(results);
        }
        return output.has("jobId");
    }

    @Override
    public List<String> render(JsonObject output) {
        if (output == null) {
            return null;
        }
        if (output.has("results") && output.get("results").isJsonArray()) {
            JsonArray results = output.getAsJsonArray("results");
            if (isAeListResults(results)) {
                return formatAeList(results, getString(output, "nextPageToken"), getString(output, "error"));
            }
        }
        if (output.has("jobId")) {
            return formatJobStatus(output);
        }
        return null;
    }

    private boolean isAeListResults(JsonArray results) {
        JsonObject first = firstObject(results);
        return first != null && first.has("itemId") && first.has("amount");
    }

    private List<String> formatAeList(JsonArray results, String nextPageToken, String error) {
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
                String label = ToolOutputFormatter.formatItemTag(itemId) + " — " + amount;
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

    private List<String> formatJobStatus(JsonObject obj) {
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
                lines.add("• " + amount + "x " + ToolOutputFormatter.formatItemTag(itemId));
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

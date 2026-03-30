package space.controlnet.mineagent.core.tools;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Builds a structured tool message payload for chat history and UI rendering.
 */
public final class ToolMessagePayload {
    private static final Gson GSON = new Gson();

    private ToolMessagePayload() {
    }

    public static String wrap(ToolCall call, ToolResult result) {
        return wrap(call, result, null);
    }

    public static String wrap(ToolCall call, ToolResult result, String thinking) {
        if (call == null && result == null && (thinking == null || thinking.isBlank())) {
            return null;
        }
        JsonObject obj = new JsonObject();
        if (thinking != null && !thinking.isBlank()) {
            obj.addProperty("thinking", thinking);
        }
        if (call != null) {
            obj.addProperty("tool", call.toolName());
            addJsonOrString(obj, "args", call.argsJson());
        }
        if (result != null) {
            addJsonOrString(obj, "output", result.payloadJson());
            if (result.error() != null && result.error().message() != null && !result.error().message().isBlank()) {
                obj.addProperty("error", result.error().message());
            }
        }
        return GSON.toJson(obj);
    }

    public static ToolPayload parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(raw);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject obj = element.getAsJsonObject();
            if (!obj.has("tool") && !obj.has("thinking") && !obj.has("args") && !obj.has("output") && !obj.has("error")) {
                return null;
            }
            String tool = getString(obj, "tool");
            String thinking = getString(obj, "thinking");
            String args = stringifyElement(obj.get("args"));
            String output = stringifyElement(obj.get("output"));
            String error = getString(obj, "error");
            return new ToolPayload(tool, thinking, args, output, error);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void addJsonOrString(JsonObject obj, String key, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            JsonElement element = JsonParser.parseString(raw);
            obj.add(key, element);
        } catch (Exception e) {
            obj.addProperty(key, raw);
        }
    }

    private static String stringifyElement(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                return element.getAsString();
            } catch (Exception ignored) {
                return element.toString();
            }
        }
        return element.toString();
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
}

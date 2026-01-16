package space.controlnet.chatae.core.tools;

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
        if (call == null && result == null) {
            return null;
        }
        JsonObject obj = new JsonObject();
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
}

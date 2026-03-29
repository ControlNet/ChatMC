package space.controlnet.chatmc.common.tools.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import space.controlnet.chatmc.core.tools.ToolPayload;
import space.controlnet.chatmc.core.tools.ToolRender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class McpFallbackRenderer {
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();
    private static final List<String> SUMMARY_ARG_PREFERENCE = List.of("query", "itemId", "path", "url", "id");

    private McpFallbackRenderer() {
    }

    static ToolRender render(ToolPayload payload) {
        String toolName = payload == null ? null : payload.tool();
        JsonObject args = parseJsonObject(payload == null ? null : payload.argsJson());
        JsonObject output = parseJsonObject(payload == null ? null : payload.outputJson());
        return new ToolRender(
                buildSummary(toolName, args),
                List.of(),
                renderLines(output),
                payload == null ? null : payload.error()
        );
    }

    static List<String> renderLines(String rawOutputJson) {
        return renderLines(parseJsonObject(rawOutputJson));
    }

    public static List<String> renderLines(JsonObject output) {
        if (!isNormalizedMcpEnvelope(output)) {
            return null;
        }

        List<String> textLines = extractTextLines(output);
        if (!textLines.isEmpty()) {
            return List.copyOf(textLines);
        }

        List<String> structuredLines = extractStructuredLines(output);
        if (!structuredLines.isEmpty()) {
            return List.copyOf(structuredLines);
        }

        List<String> unsupportedLines = extractUnsupportedContentLines(output);
        if (!unsupportedLines.isEmpty()) {
            return List.copyOf(unsupportedLines);
        }

        return List.of("No result.");
    }

    static String buildSummary(String toolName, JsonObject args) {
        String normalizedToolName = toolName == null || toolName.isBlank() ? "mcp.unknown" : toolName;
        String preview = selectArgPreview(args);
        if (preview == null || preview.isBlank()) {
            return normalizedToolName;
        }
        return normalizedToolName + " (" + preview + ")";
    }

    private static boolean isNormalizedMcpEnvelope(JsonObject output) {
        if (output == null) {
            return false;
        }
        return output.has("serverAlias")
                && output.has("qualifiedTool")
                && output.has("remoteTool")
                && output.has("isError")
                && output.has("textContent")
                && output.has("structuredContent")
                && output.has("unsupportedContentTypes");
    }

    private static String selectArgPreview(JsonObject args) {
        if (args == null || args.entrySet().isEmpty()) {
            return null;
        }

        for (String preferredKey : SUMMARY_ARG_PREFERENCE) {
            String preview = previewEntry(preferredKey, args.get(preferredKey));
            if (preview != null) {
                return preview;
            }
        }

        for (Map.Entry<String, JsonElement> entry : args.entrySet()) {
            String preview = previewEntry(entry.getKey(), entry.getValue());
            if (preview != null) {
                return preview;
            }
        }
        return null;
    }

    private static String previewEntry(String key, JsonElement value) {
        String scalarValue = scalarPreview(value);
        if (scalarValue == null || scalarValue.isBlank()) {
            return null;
        }
        return key + "=" + scalarValue;
    }

    private static String scalarPreview(JsonElement value) {
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        if (value.getAsJsonPrimitive().isString()) {
            String normalized = value.getAsString().trim().replaceAll("\\s+", " ");
            return normalized.isBlank() ? null : normalized;
        }
        return value.getAsJsonPrimitive().getAsString();
    }

    private static List<String> extractTextLines(JsonObject output) {
        JsonArray textContent = getArray(output, "textContent");
        if (textContent == null || textContent.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (JsonElement entry : textContent) {
            if (entry == null || entry.isJsonNull() || !entry.isJsonPrimitive()
                    || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            splitMultilineText(entry.getAsString(), lines);
        }
        return List.copyOf(lines);
    }

    private static List<String> extractStructuredLines(JsonObject output) {
        JsonElement structuredContent = output.get("structuredContent");
        if (structuredContent == null || structuredContent.isJsonNull()) {
            return List.of();
        }
        if (structuredContent.isJsonPrimitive()) {
            String scalarValue = scalarPreview(structuredContent);
            if (scalarValue == null || scalarValue.isBlank()) {
                return List.of();
            }
            List<String> lines = new ArrayList<>();
            splitMultilineText(scalarValue, lines);
            return List.copyOf(lines);
        }
        String prettyJson = PRETTY_GSON.toJson(structuredContent);
        return List.of(prettyJson.split("\\R", -1));
    }

    private static List<String> extractUnsupportedContentLines(JsonObject output) {
        JsonArray unsupportedContentTypes = getArray(output, "unsupportedContentTypes");
        if (unsupportedContentTypes == null || unsupportedContentTypes.isEmpty()) {
            return List.of();
        }

        List<String> lines = new ArrayList<>();
        for (JsonElement entry : unsupportedContentTypes) {
            if (entry == null || entry.isJsonNull() || !entry.isJsonPrimitive()
                    || !entry.getAsJsonPrimitive().isString()) {
                continue;
            }
            lines.add("Unsupported MCP content type: " + entry.getAsString());
        }
        return List.copyOf(lines);
    }

    private static void splitMultilineText(String text, List<String> lines) {
        if (text == null || text.isBlank()) {
            return;
        }
        for (String line : text.split("\\R", -1)) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
    }

    private static JsonArray getArray(JsonObject object, String fieldName) {
        if (object == null || fieldName == null || !object.has(fieldName) || object.get(fieldName).isJsonNull()) {
            return null;
        }
        try {
            return object.getAsJsonArray(fieldName);
        } catch (Exception ignored) {
            return null;
        }
    }

    static JsonObject parseJsonObject(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(rawJson);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (JsonParseException ignored) {
            return null;
        }
    }
}

package space.controlnet.mineagent.common.client.render;

import com.google.gson.JsonObject;
import space.controlnet.mineagent.common.tools.http.HttpToolResultEnvelope;

import java.util.ArrayList;
import java.util.List;

public final class HttpToolOutputRenderer implements ToolOutputRenderer {
    private static final int BODY_PREVIEW_LIMIT = 160;

    @Override
    public boolean canRender(JsonObject output) {
        return HttpToolResultEnvelope.KIND.equals(getString(output, "kind"));
    }

    @Override
    public List<String> render(JsonObject output) {
        if (!canRender(output)) {
            return null;
        }

        JsonObject request = getObject(output, "request");
        if (request == null) {
            return null;
        }

        JsonObject failure = getObject(output, "failure");
        if (failure != null) {
            return renderFailure(request, failure, getBoolean(output, "truncated"));
        }

        JsonObject response = getObject(output, "response");
        if (response == null) {
            return null;
        }
        return renderSuccess(request, response, getBoolean(output, "truncated"));
    }

    private static List<String> renderSuccess(JsonObject request, JsonObject response, boolean truncated) {
        List<String> lines = new ArrayList<>();
        lines.add("HTTP " + requestMethod(request) + " " + responseUrl(request, response));
        lines.add("Status: " + getInt(response, "statusCode", -1));
        lines.add("Content-Type: " + formatContentType(response));
        lines.add("Truncated: " + yesNo(truncated));
        lines.add("Body: " + formatSuccessBody(request, response));
        return List.copyOf(lines);
    }

    private static List<String> renderFailure(JsonObject request, JsonObject failure, boolean truncated) {
        List<String> lines = new ArrayList<>();
        lines.add("HTTP " + requestMethod(request) + " " + requestUrl(request));
        lines.add("Failure: " + formatFailure(failure));
        lines.add("Truncated: " + yesNo(truncated));
        return List.copyOf(lines);
    }

    private static String formatSuccessBody(JsonObject request, JsonObject response) {
        long bodyBytes = getLong(response, "bodyBytes", 0L);
        String responseMode = getString(request, "responseMode");
        if ("bytes".equals(responseMode)) {
            return bodyBytes + " bytes (responseMode=bytes; preview omitted)";
        }

        String bodyBase64 = getString(response, "bodyBase64");
        if (bodyBase64 != null && !bodyBase64.isBlank()) {
            return bodyBytes + " bytes (binary; preview omitted)";
        }

        String bodyText = getString(response, "bodyText");
        if (bodyText == null) {
            return bodyBytes == 0L ? "empty" : (bodyBytes + " bytes");
        }
        return preview(bodyText);
    }

    private static String formatFailure(JsonObject failure) {
        String code = blankToFallback(getString(failure, "code"), "unknown_failure");
        String message = blankToFallback(getString(failure, "message"), "no message");
        return code + " - " + message;
    }

    private static String formatContentType(JsonObject response) {
        String contentType = getString(response, "contentType");
        String charset = getString(response, "charset");
        if (contentType == null || contentType.isBlank()) {
            return charset == null || charset.isBlank() ? "unknown" : ("unknown; charset=" + charset);
        }
        return charset == null || charset.isBlank() ? contentType : (contentType + "; charset=" + charset);
    }

    private static String preview(String bodyText) {
        String normalized = bodyText
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "\\n");
        if (normalized.length() <= BODY_PREVIEW_LIMIT) {
            return normalized;
        }
        return normalized.substring(0, BODY_PREVIEW_LIMIT - 3) + "...";
    }

    private static String responseUrl(JsonObject request, JsonObject response) {
        return blankToFallback(getString(response, "finalUrl"), requestUrl(request));
    }

    private static String requestUrl(JsonObject request) {
        return blankToFallback(getString(request, "url"), "?");
    }

    private static String requestMethod(JsonObject request) {
        return blankToFallback(getString(request, "method"), "GET");
    }

    private static String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (obj == null || key == null || !obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.getAsJsonObject(key);
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

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }
}

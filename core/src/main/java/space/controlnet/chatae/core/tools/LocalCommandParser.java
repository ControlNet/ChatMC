package space.controlnet.chatae.core.tools;

import com.google.gson.Gson;

import java.util.Locale;

public final class LocalCommandParser {
    private static final Gson GSON = new Gson();

    private LocalCommandParser() {
    }

    public static ToolCall parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("recipes.search ")) {
            String query = text.substring("recipes.search ".length()).trim();
            return new ToolCall("recipes.search", GSON.toJson(new ToolArgs.RecipeSearchArgs(
                    query, null, 10, null, null, null, null, null)));
        }

        if (lower.startsWith("recipes.get ")) {
            String id = text.substring("recipes.get ".length()).trim();
            return new ToolCall("recipes.get", GSON.toJson(new ToolArgs.RecipeGetArgs(id)));
        }

        if (lower.startsWith("ae2.list_items")) {
            String query = parseOptionalArg(text, "ae2.list_items");
            return new ToolCall("ae2.list_items", GSON.toJson(new ToolArgs.Ae2ListArgs(query, false, 50, null)));
        }

        if (lower.startsWith("ae2.list_craftables")) {
            String query = parseOptionalArg(text, "ae2.list_craftables");
            return new ToolCall("ae2.list_craftables", GSON.toJson(new ToolArgs.Ae2ListArgs(query, true, 50, null)));
        }

        if (lower.startsWith("ae2.simulate_craft ")) {
            String args = text.substring("ae2.simulate_craft ".length()).trim();
            String[] parts = args.split("\\s+", 2);
            String itemId = parts[0];
            long count = parts.length > 1 ? parseLong(parts[1], 1) : 1;
            return new ToolCall("ae2.simulate_craft", GSON.toJson(new ToolArgs.Ae2CraftArgs(itemId, count, null)));
        }

        if (lower.startsWith("ae2.request_craft ")) {
            String args = text.substring("ae2.request_craft ".length()).trim();
            String[] parts = args.split("\\s+", 2);
            String itemId = parts[0];
            long count = parts.length > 1 ? parseLong(parts[1], 1) : 1;
            return new ToolCall("ae2.request_craft", GSON.toJson(new ToolArgs.Ae2CraftArgs(itemId, count, null)));
        }

        if (lower.startsWith("ae2.job_status ")) {
            String jobId = text.substring("ae2.job_status ".length()).trim();
            return new ToolCall("ae2.job_status", GSON.toJson(new ToolArgs.Ae2JobArgs(jobId)));
        }

        if (lower.startsWith("ae2.job_cancel ")) {
            String jobId = text.substring("ae2.job_cancel ".length()).trim();
            return new ToolCall("ae2.job_cancel", GSON.toJson(new ToolArgs.Ae2JobArgs(jobId)));
        }

        return null;
    }

    private static long parseLong(String raw, long fallback) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String parseOptionalArg(String text, String prefix) {
        String remainder = text.substring(prefix.length()).trim();
        return remainder.isEmpty() ? "" : remainder;
    }
}

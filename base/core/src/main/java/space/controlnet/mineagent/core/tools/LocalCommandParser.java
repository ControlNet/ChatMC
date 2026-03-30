package space.controlnet.mineagent.core.tools;

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
        if (lower.startsWith("mc.find_recipes ")) {
            String itemId = text.substring("mc.find_recipes ".length()).trim();
            return new ToolCall("mc.find_recipes", GSON.toJson(new ToolArgs.McFindRecipesArgs(itemId, null, 10)));
        }

        if (lower.startsWith("mc.find_usage ")) {
            String itemId = text.substring("mc.find_usage ".length()).trim();
            return new ToolCall("mc.find_usage", GSON.toJson(new ToolArgs.McFindUsageArgs(itemId, null, 10)));
        }

        return null;
    }

}

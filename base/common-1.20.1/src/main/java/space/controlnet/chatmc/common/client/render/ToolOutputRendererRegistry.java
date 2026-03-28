package space.controlnet.chatmc.common.client.render;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class ToolOutputRendererRegistry {
    private static final List<ToolOutputRenderer> RENDERERS = new CopyOnWriteArrayList<>();

    private ToolOutputRendererRegistry() {
    }

    public static void register(ToolOutputRenderer renderer) {
        if (renderer != null && !RENDERERS.contains(renderer)) {
            RENDERERS.add(renderer);
        }
    }

    public static List<String> tryRender(JsonObject output) {
        if (output == null) {
            return null;
        }
        for (ToolOutputRenderer renderer : RENDERERS) {
            if (renderer.canRender(output)) {
                List<String> result = renderer.render(output);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static void clear() {
        RENDERERS.clear();
    }
}

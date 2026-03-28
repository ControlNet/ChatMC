package space.controlnet.chatmc.common.client.render;

import com.google.gson.JsonObject;

import java.util.List;

public interface ToolOutputRenderer {
    boolean canRender(JsonObject output);

    List<String> render(JsonObject output);
}

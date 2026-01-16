package space.controlnet.chatae.core.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Shared Gson instance with Optional support for tool/result serialization.
 */
public final class JsonSupport {
    public static final Gson GSON = new GsonBuilder()
            .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
            .create();

    private JsonSupport() {
    }
}

package space.controlnet.chatae.core.agent;

import dev.langchain4j.model.chat.ChatModel;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LlmRuntime {
    private static final AtomicReference<ChatModel> MODEL = new AtomicReference<>();

    private LlmRuntime() {
    }

    public static void reload(LlmConfig config) {
        Optional<ChatModel> model = LlmModelFactory.build(config);
        MODEL.set(model.orElse(null));
    }

    public static Optional<ChatModel> model() {
        return Optional.ofNullable(MODEL.get());
    }

    public static void clear() {
        MODEL.set(null);
    }
}

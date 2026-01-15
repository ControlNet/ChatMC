package space.controlnet.chatae.core.agent;

import dev.langchain4j.model.chat.ChatModel;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

public final class LlmRuntime {
    private static final AtomicReference<ChatModel> MODEL = new AtomicReference<>();

    private LlmRuntime() {
    }

    public static boolean reload(LlmConfig config) {
        Optional<ChatModel> model = LlmModelFactory.build(config);
        if (model.isPresent()) {
            MODEL.set(model.get());
            return true;
        }
        return false;
    }

    public static Optional<ChatModel> model() {
        return Optional.ofNullable(MODEL.get());
    }

    public static void clear() {
        MODEL.set(null);
    }
}

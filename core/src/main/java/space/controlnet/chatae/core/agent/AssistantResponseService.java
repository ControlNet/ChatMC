package space.controlnet.chatae.core.agent;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

import java.util.Optional;

public final class AssistantResponseService {
    private final Logger logger;

    public AssistantResponseService(Logger logger) {
        this.logger = logger;
    }

    public Optional<String> generate(String prompt) {
        Optional<ChatModel> model = LlmRuntime.model();
        if (model.isEmpty()) {
            return Optional.empty();
        }
        String rendered = prompt == null ? "" : prompt.trim();
        if (rendered.isEmpty()) {
            return Optional.empty();
        }
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(java.util.List.of(new UserMessage(rendered)))
                    .build();
            var response = model.get().chat(request);
            String content = response.aiMessage().text();
            return content == null || content.isBlank() ? Optional.empty() : Optional.of(content);
        } catch (Exception e) {
            logger.warn("Assistant response generation failed", e);
            return Optional.empty();
        }
    }
}

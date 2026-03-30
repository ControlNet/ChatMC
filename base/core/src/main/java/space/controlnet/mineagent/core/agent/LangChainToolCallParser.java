package space.controlnet.mineagent.core.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;

import space.controlnet.mineagent.core.tools.ToolCall;

import java.util.Optional;

/**
 * LangChain4j-based implementation of ToolCallParser.
 * Uses OpenAI API to parse natural language into tool calls.
 */
public final class LangChainToolCallParser implements ToolCallParser {
    private static final Gson GSON = new Gson();

    private final ChatModel model;
    private final Logger logger;

    private LangChainToolCallParser(ChatModel model, Logger logger) {
        this.model = model;
        this.logger = logger;
    }

    public static Optional<LangChainToolCallParser> create(Logger logger) {
        Optional<ChatModel> model = LlmRuntime.model();
        return model.map(chatModel -> new LangChainToolCallParser(chatModel, logger));
    }

    @Override
    public Optional<ToolCall> parse(String prompt) {
        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(java.util.List.of(new UserMessage(prompt)))
                    .build();
            var response = model.chat(request);
            String content = response.aiMessage().text();
            JsonObject obj = GSON.fromJson(content, JsonObject.class);
            if (obj == null || !obj.has("tool") || !obj.has("args")) {
                return Optional.empty();
            }
            String tool = obj.get("tool").getAsString();
            String argsJson = obj.get("args").toString();
            ToolCallArgsParseBoundary.validate(tool, argsJson);
            return Optional.of(new ToolCall(tool, argsJson));
        } catch (ToolCallArgsParseBoundary.ToolCallParseBoundaryException e) {
            logger.warn(e.getMessage(), null);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("LangChain parsing failed", e);
            return Optional.empty();
        }
    }
}

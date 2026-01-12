package space.controlnet.chatae.core.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import space.controlnet.chatae.core.tools.ToolCall;

import java.util.Optional;

/**
 * LangChain4j-based implementation of ToolCallParser.
 * Uses OpenAI API to parse natural language into tool calls.
 */
public final class LangChainToolCallParser implements ToolCallParser {
    private static final Gson GSON = new Gson();
    private static final String ENV_KEY = "CHATAE_OPENAI_API_KEY";

    private final ChatModel model;
    private final Logger logger;

    private LangChainToolCallParser(ChatModel model, Logger logger) {
        this.model = model;
        this.logger = logger;
    }

    public static Optional<LangChainToolCallParser> create(Logger logger) {
        String apiKey = System.getenv(ENV_KEY);
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        ChatModel model = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .strictJsonSchema(true)
                .build();
        return Optional.of(new LangChainToolCallParser(model, logger));
    }

    @Override
    public Optional<ToolCall> parse(String message) {
        String prompt = "Return ONLY JSON with fields 'tool' and 'args'. "
                + "Tool must be one of: recipes.search, recipes.get, ae2.list_items, ae2.list_craftables, "
                + "ae2.simulate_craft, ae2.request_craft, ae2.job_status, ae2.job_cancel. "
                + "Args schema:\n"
                + "- recipes.search: {query, pageToken?, limit, modId?, recipeType?, outputItemId?, ingredientItemId?, tagId?}\n"
                + "- recipes.get: {recipeId}\n"
                + "- ae2.list_items: {query, craftableOnly, limit, pageToken?}\n"
                + "- ae2.list_craftables: {query, craftableOnly, limit, pageToken?}\n"
                + "- ae2.simulate_craft: {itemId, count}\n"
                + "- ae2.request_craft: {itemId, count, cpuName?}\n"
                + "- ae2.job_status: {jobId}\n"
                + "- ae2.job_cancel: {jobId}.";

        try {
            ChatRequest request = ChatRequest.builder()
                    .messages(java.util.List.of(new UserMessage(prompt + "\nUser: " + message)))
                    .build();
            var response = model.chat(request);
            String content = response.aiMessage().text();
            JsonObject obj = GSON.fromJson(content, JsonObject.class);
            if (obj == null || !obj.has("tool") || !obj.has("args")) {
                return Optional.empty();
            }
            String tool = obj.get("tool").getAsString();
            String argsJson = obj.get("args").toString();
            return Optional.of(new ToolCall(tool, argsJson));
        } catch (Exception e) {
            logger.warn("LangChain parsing failed", e);
            return Optional.empty();
        }
    }
}

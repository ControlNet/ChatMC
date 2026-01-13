package space.controlnet.chatae.core.agent;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import space.controlnet.chatae.core.tools.ToolCall;

import java.util.Optional;

/**
 * Service that asks the LLM to decide the next action in the agent loop.
 * Returns an AgentDecision with either a tool call or a direct response.
 */
public final class AgentReasoningService {
    private static final Gson GSON = new Gson();

    private final Logger logger;

    public AgentReasoningService(Logger logger) {
        this.logger = logger;
    }

    /**
     * Given a rendered prompt, ask the LLM to decide the next action.
     *
     * @param prompt The fully rendered prompt including conversation history
     * @return AgentDecision with either tool_call or respond action
     */
    public Optional<AgentDecision> reason(String prompt) {
        Optional<ChatModel> modelOpt = LlmRuntime.model();
        if (modelOpt.isEmpty()) {
            logger.warn("LLM model not available for reasoning", null);
            return Optional.empty();
        }

        try {
            ChatModel model = modelOpt.get();
            ChatRequest request = ChatRequest.builder()
                    .messages(java.util.List.of(new UserMessage(prompt)))
                    .build();

            var response = model.chat(request);
            String content = response.aiMessage().text();

            return parseDecision(content);
        } catch (Exception e) {
            logger.warn("Agent reasoning failed", e);
            return Optional.empty();
        }
    }

    /**
     * Parse the LLM's JSON response into an AgentDecision.
     */
    private Optional<AgentDecision> parseDecision(String content) {
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        try {
            // Strip markdown code blocks if present
            String json = content.trim();
            if (json.startsWith("```json")) {
                json = json.substring(7);
            } else if (json.startsWith("```")) {
                json = json.substring(3);
            }
            if (json.endsWith("```")) {
                json = json.substring(0, json.length() - 3);
            }
            json = json.trim();

            JsonObject obj = GSON.fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("action")) {
                logger.warn("Invalid agent decision: missing 'action' field", null);
                return Optional.empty();
            }

            String action = obj.get("action").getAsString();
            String thinking = obj.has("thinking") && !obj.get("thinking").isJsonNull()
                    ? obj.get("thinking").getAsString()
                    : null;

            if ("tool_call".equals(action)) {
                if (!obj.has("tool") || !obj.has("args")) {
                    logger.warn("Invalid tool_call decision: missing 'tool' or 'args'", null);
                    return Optional.empty();
                }
                String tool = obj.get("tool").getAsString();
                String argsJson = obj.get("args").toString();
                return Optional.of(AgentDecision.toolCall(new ToolCall(tool, argsJson), thinking));
            } else if ("respond".equals(action)) {
                if (!obj.has("response")) {
                    logger.warn("Invalid respond decision: missing 'response'", null);
                    return Optional.empty();
                }
                String response = obj.get("response").getAsString();
                return Optional.of(AgentDecision.respond(response, thinking));
            } else {
                logger.warn("Unknown action type: " + action, null);
                return Optional.empty();
            }
        } catch (Exception e) {
            logger.warn("Failed to parse agent decision JSON: " + content, e);
            return Optional.empty();
        }
    }
}

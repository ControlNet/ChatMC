package space.controlnet.chatae.core.agent;

public enum PromptId {
    TOOL_CALL_PARSER_MAIN("tool_call_parser.main"),
    ASSISTANT_RESPONSE_MAIN("assistant_response.main"),
    AGENT_REASON("agent.reason");

    private final String id;

    PromptId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}

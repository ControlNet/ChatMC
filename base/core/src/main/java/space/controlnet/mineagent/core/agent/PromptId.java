package space.controlnet.mineagent.core.agent;

public enum PromptId {
    AGENT_REASON("agent.reason");

    private final String id;

    PromptId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}

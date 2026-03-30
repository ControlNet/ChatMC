package space.controlnet.mineagent.core.tools.mcp;

public enum McpTransportKind {
    STDIO("stdio"),
    HTTP("http");

    private final String jsonName;

    McpTransportKind(String jsonName) {
        this.jsonName = jsonName;
    }

    public String jsonName() {
        return jsonName;
    }

    public static McpTransportKind fromJsonName(String raw) {
        if (raw == null) {
            return null;
        }
        for (McpTransportKind value : values()) {
            if (value.jsonName.equals(raw)) {
                return value;
            }
        }
        return null;
    }
}

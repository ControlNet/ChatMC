package space.controlnet.chatmc.core.tools.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record McpConfig(Map<String, McpServerConfig> mcpServers) {
    public McpConfig {
        mcpServers = mcpServers == null ? Map.of() : unmodifiableLinkedMap(mcpServers);
    }

    public static McpConfig defaults() {
        return new McpConfig(Map.of());
    }

    private static Map<String, McpServerConfig> unmodifiableLinkedMap(Map<String, McpServerConfig> source) {
        Map<String, McpServerConfig> copy = new LinkedHashMap<>();
        for (Map.Entry<String, McpServerConfig> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}

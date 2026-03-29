package space.controlnet.chatmc.core.tools.mcp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record McpServerConfig(
        McpTransportKind type,
        Optional<String> command,
        List<String> args,
        Map<String, String> env,
        Optional<String> cwd,
        Optional<String> url
) {
    public McpServerConfig {
        command = command == null ? Optional.empty() : command;
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : unmodifiableLinkedMap(env);
        cwd = cwd == null ? Optional.empty() : cwd;
        url = url == null ? Optional.empty() : url;
    }

    public static McpServerConfig stdio(String command, List<String> args, Map<String, String> env, Optional<String> cwd) {
        return new McpServerConfig(McpTransportKind.STDIO, Optional.ofNullable(command), args, env, cwd, Optional.empty());
    }

    public static McpServerConfig http(String url) {
        return new McpServerConfig(McpTransportKind.HTTP, Optional.empty(), List.of(), Map.of(), Optional.empty(), Optional.ofNullable(url));
    }

    private static Map<String, String> unmodifiableLinkedMap(Map<String, String> source) {
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            copy.put(entry.getKey(), entry.getValue());
        }
        return Collections.unmodifiableMap(copy);
    }
}

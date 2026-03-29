package space.controlnet.chatmc.core.tools.mcp;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class McpConfigValidator {
    static final Pattern ALIAS_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,31}$");

    private McpConfigValidator() {
    }

    public static List<String> validate(McpConfig config) {
        List<String> errors = new ArrayList<>();
        if (config == null) {
            errors.add("MCP config is missing.");
            return errors;
        }

        for (Map.Entry<String, McpServerConfig> entry : config.mcpServers().entrySet()) {
            String alias = entry.getKey();
            McpServerConfig server = entry.getValue();

            if (!ALIAS_PATTERN.matcher(alias).matches()) {
                errors.add("Server alias '" + alias + "' must match ^[a-z0-9][a-z0-9-]{0,31}$.");
            }
            if (server == null) {
                errors.add("Server '" + alias + "' config is missing.");
                continue;
            }

            if (server.type() == null) {
                errors.add("Server '" + alias + "' type is required.");
                continue;
            }

            if (server.type() == McpTransportKind.STDIO) {
                validateStdio(alias, server, errors);
                continue;
            }
            if (server.type() == McpTransportKind.HTTP) {
                validateHttp(alias, server, errors);
            }
        }
        return errors;
    }

    private static void validateStdio(String alias, McpServerConfig server, List<String> errors) {
        if (server.command().filter(value -> !value.isBlank()).isEmpty()) {
            errors.add("Server '" + alias + "' command is required for stdio.");
        }
        if (server.url().isPresent()) {
            errors.add("Server '" + alias + "' url is not allowed for stdio.");
        }
        if (server.cwd().filter(String::isBlank).isPresent()) {
            errors.add("Server '" + alias + "' cwd must not be blank.");
        }
    }

    private static void validateHttp(String alias, McpServerConfig server, List<String> errors) {
        String url = server.url().orElse("");
        if (url.isBlank()) {
            errors.add("Server '" + alias + "' url is required for http.");
        } else if (!isHttpUrl(url)) {
            errors.add("Server '" + alias + "' url must start with http:// or https://.");
        }

        if (server.command().isPresent()) {
            errors.add("Server '" + alias + "' command is not allowed for http.");
        }
        if (!server.args().isEmpty()) {
            errors.add("Server '" + alias + "' args is not allowed for http.");
        }
        if (!server.env().isEmpty()) {
            errors.add("Server '" + alias + "' env is not allowed for http.");
        }
        if (server.cwd().isPresent()) {
            errors.add("Server '" + alias + "' cwd is not allowed for http.");
        }
    }

    private static boolean isHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = uri.getScheme();
            return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}

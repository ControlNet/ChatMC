package space.controlnet.mineagent.core.tools.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class McpConfigParser {
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Set<String> TOP_LEVEL_KEYS = Set.of("mcpServers");
    private static final Set<String> KNOWN_SERVER_KEYS = Set.of("type", "command", "args", "env", "cwd", "url");
    private static final Set<String> UNSUPPORTED_SERVER_KEYS = Set.of("oauth", "headers", "bearerTokenEnv", "env_vars");

    private McpConfigParser() {
    }

    public static McpConfig parse(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("MCP config reader is missing.");
        }
        try (JsonReader jsonReader = new JsonReader(reader)) {
            jsonReader.setLenient(false);
            return parseRoot(jsonReader);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Failed to read MCP config JSON.", exception);
        } catch (JsonParseException exception) {
            throw new IllegalArgumentException("Failed to parse MCP config JSON.", exception);
        }
    }

    public static void writeJson(Writer writer, McpConfig config) {
        if (writer == null) {
            throw new IllegalArgumentException("MCP config writer is missing.");
        }
        try {
            PRETTY_GSON.toJson(toJson(config == null ? McpConfig.defaults() : config), writer);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to write MCP JSON config", exception);
        }
    }

    private static McpConfig parseRoot(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IllegalArgumentException("Root config must be a JSON object.");
        }

        reader.beginObject();
        boolean sawMcpServers = false;
        Map<String, McpServerConfig> servers = Map.of();
        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!TOP_LEVEL_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown top-level key: " + key + ".");
            }
            if ("mcpServers".equals(key)) {
                if (sawMcpServers) {
                    throw new IllegalArgumentException("Duplicate top-level key: mcpServers.");
                }
                sawMcpServers = true;
                servers = readServers(reader);
            }
        }
        reader.endObject();

        if (!sawMcpServers) {
            throw new IllegalArgumentException("mcpServers is required.");
        }
        return new McpConfig(servers);
    }

    private static Map<String, McpServerConfig> readServers(JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IllegalArgumentException("mcpServers must be a JSON object.");
        }

        Map<String, McpServerConfig> servers = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String alias = reader.nextName();
            if (servers.containsKey(alias)) {
                throw new IllegalArgumentException("Duplicate server alias '" + alias + "'.");
            }
            servers.put(alias, parseServer(alias, reader));
        }
        reader.endObject();
        return servers;
    }

    private static McpServerConfig parseServer(String alias, JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IllegalArgumentException("Server '" + alias + "' must be a JSON object.");
        }

        reader.beginObject();
        Set<String> seenKeys = new java.util.LinkedHashSet<>();
        McpTransportKind type = null;
        Optional<String> command = Optional.empty();
        List<String> args = List.of();
        Map<String, String> env = Map.of();
        Optional<String> cwd = Optional.empty();
        Optional<String> url = Optional.empty();

        while (reader.hasNext()) {
            String key = reader.nextName();
            if (!seenKeys.add(key)) {
                throw new IllegalArgumentException("Server '" + alias + "' has duplicate field '" + key + "'.");
            }
            if (UNSUPPORTED_SERVER_KEYS.contains(key)) {
                throw new IllegalArgumentException("Server '" + alias + "' uses unsupported field '" + key + "'.");
            }
            if (!KNOWN_SERVER_KEYS.contains(key)) {
                throw new IllegalArgumentException("Server '" + alias + "' has unknown key '" + key + "'.");
            }

            switch (key) {
                case "type" -> {
                    String rawType = readRequiredString(alias, key, reader);
                    type = McpTransportKind.fromJsonName(rawType);
                    if (type == null) {
                        throw new IllegalArgumentException("Server '" + alias + "' has unsupported type '" + rawType + "'.");
                    }
                }
                case "command" -> command = Optional.of(readRequiredString(alias, key, reader));
                case "args" -> args = readStringArray(alias, key, reader);
                case "env" -> env = readStringMap(alias, key, reader);
                case "cwd" -> cwd = Optional.of(readRequiredString(alias, key, reader));
                case "url" -> url = Optional.of(readRequiredString(alias, key, reader));
                default -> throw new IllegalArgumentException("Server '" + alias + "' has unknown key '" + key + "'.");
            }
        }
        reader.endObject();

        if (type == null) {
            throw new IllegalArgumentException("Server '" + alias + "' field 'type' is required.");
        }

        return new McpServerConfig(type, command, args, env, cwd, url);
    }

    private static String readRequiredString(String alias, String key, JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.STRING) {
            if (reader.peek() == JsonToken.NULL) {
                reader.nextNull();
            }
            throw new IllegalArgumentException("Server '" + alias + "' field '" + key + "' must be a string.");
        }
        return reader.nextString();
    }

    private static List<String> readStringArray(String alias, String key, JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_ARRAY) {
            throw new IllegalArgumentException("Server '" + alias + "' field '" + key + "' must be an array of strings.");
        }

        List<String> values = new java.util.ArrayList<>();
        reader.beginArray();
        while (reader.hasNext()) {
            if (reader.peek() != JsonToken.STRING) {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                }
                throw new IllegalArgumentException("Server '" + alias + "' field '" + key + "' must be an array of strings.");
            }
            values.add(reader.nextString());
        }
        reader.endArray();
        return List.copyOf(values);
    }

    private static Map<String, String> readStringMap(String alias, String key, JsonReader reader) throws IOException {
        if (reader.peek() != JsonToken.BEGIN_OBJECT) {
            throw new IllegalArgumentException("Server '" + alias + "' field '" + key + "' must be an object of strings.");
        }

        Map<String, String> values = new LinkedHashMap<>();
        reader.beginObject();
        while (reader.hasNext()) {
            String envKey = reader.nextName();
            if (values.containsKey(envKey)) {
                throw new IllegalArgumentException("Server '" + alias + "' field '" + key + "' has duplicate key '" + envKey + "'.");
            }
            if (reader.peek() != JsonToken.STRING) {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                }
                throw new IllegalArgumentException("Server '" + alias + "' field '" + key + "' must be an object of strings.");
            }
            values.put(envKey, reader.nextString());
        }
        reader.endObject();
        return values;
    }

    private static JsonObject toJson(McpConfig config) {
        JsonObject root = new JsonObject();
        JsonObject servers = new JsonObject();
        for (Map.Entry<String, McpServerConfig> entry : config.mcpServers().entrySet()) {
            servers.add(entry.getKey(), toJson(entry.getValue()));
        }
        root.add("mcpServers", servers);
        return root;
    }

    private static JsonObject toJson(McpServerConfig server) {
        JsonObject json = new JsonObject();
        if (server.type() != null) {
            json.addProperty("type", server.type().jsonName());
        }
        server.command().ifPresent(value -> json.addProperty("command", value));
        if (!server.args().isEmpty()) {
            JsonArray args = new JsonArray();
            for (String value : server.args()) {
                args.add(value);
            }
            json.add("args", args);
        }
        if (!server.env().isEmpty()) {
            JsonObject env = new JsonObject();
            for (Map.Entry<String, String> entry : server.env().entrySet()) {
                env.addProperty(entry.getKey(), entry.getValue());
            }
            json.add("env", env);
        }
        server.cwd().ifPresent(value -> json.addProperty("cwd", value));
        server.url().ifPresent(value -> json.addProperty("url", value));
        return json;
    }
}

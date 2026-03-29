package space.controlnet.chatmc.common.tools.mcp;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import space.controlnet.chatmc.core.tools.AgentTool;
import space.controlnet.chatmc.core.tools.AgentToolSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class McpSchemaMapper {
    private static final Gson PRETTY_GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private McpSchemaMapper() {
    }

    public static McpProjectedTool project(String serverAlias, McpRemoteTool remoteTool) {
        Objects.requireNonNull(remoteTool, "remoteTool");

        String qualifiedToolName = qualifiedToolName(serverAlias, remoteTool.name());
        AgentTool toolSpec = new AgentToolSpec(
                qualifiedToolName,
                remoteTool.description().orElse(""),
                normalizeArgsSchema(remoteTool.inputSchema()),
                buildArgsDescription(remoteTool.inputSchema()),
                "",
                List.of(),
                List.of(),
                McpFallbackRenderer::render
        );
        return new McpProjectedTool(serverAlias, remoteTool.name(), qualifiedToolName, remoteTool.readOnlyHint(), toolSpec);
    }

    public static String qualifiedToolName(String serverAlias, String remoteToolName) {
        if (serverAlias == null || serverAlias.isBlank()) {
            throw new IllegalArgumentException("MCP server alias is required.");
        }
        if (remoteToolName == null || remoteToolName.isBlank()) {
            throw new IllegalArgumentException("MCP remote tool name is required.");
        }
        return "mcp." + serverAlias + "." + remoteToolName;
    }

    private static String normalizeArgsSchema(JsonElement inputSchema) {
        if (inputSchema == null || inputSchema.isJsonNull()) {
            return "";
        }
        return PRETTY_GSON.toJson(inputSchema);
    }

    private static List<String> buildArgsDescription(JsonElement inputSchema) {
        if (inputSchema == null || !inputSchema.isJsonObject()) {
            return List.of();
        }

        JsonObject schemaObject = inputSchema.getAsJsonObject();
        JsonElement propertiesElement = schemaObject.get("properties");
        if (propertiesElement == null || !propertiesElement.isJsonObject()) {
            return List.of();
        }

        Set<String> requiredNames = extractRequiredNames(schemaObject.get("required"));
        List<String> descriptions = new ArrayList<>();
        for (var entry : propertiesElement.getAsJsonObject().entrySet()) {
            String line = buildPropertyDescription(entry.getKey(), entry.getValue(), requiredNames.contains(entry.getKey()));
            if (line != null && !line.isBlank()) {
                descriptions.add(line);
            }
        }
        return List.copyOf(descriptions);
    }

    private static Set<String> extractRequiredNames(JsonElement requiredElement) {
        if (requiredElement == null || !requiredElement.isJsonArray()) {
            return Set.of();
        }

        Set<String> requiredNames = new LinkedHashSet<>();
        JsonArray requiredArray = requiredElement.getAsJsonArray();
        for (JsonElement element : requiredArray) {
            if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                requiredNames.add(element.getAsString());
            }
        }
        return requiredNames;
    }

    private static String buildPropertyDescription(String propertyName, JsonElement propertySchema, boolean required) {
        StringBuilder line = new StringBuilder(propertyName)
                .append(": ")
                .append(required ? "required" : "optional");

        String propertyType = resolvePropertyType(propertySchema);
        if (!propertyType.isBlank()) {
            line.append(' ').append(propertyType);
        }

        String propertyDescription = extractPropertyDescription(propertySchema);
        if (!propertyDescription.isBlank()) {
            line.append(" - ").append(propertyDescription);
        }
        return line.toString();
    }

    private static String resolvePropertyType(JsonElement propertySchema) {
        if (propertySchema == null || !propertySchema.isJsonObject()) {
            return "";
        }

        JsonObject propertyObject = propertySchema.getAsJsonObject();
        JsonElement typeElement = propertyObject.get("type");
        if (typeElement != null) {
            if (typeElement.isJsonPrimitive() && typeElement.getAsJsonPrimitive().isString()) {
                return typeElement.getAsString();
            }
            if (typeElement.isJsonArray()) {
                List<String> typeNames = new ArrayList<>();
                for (JsonElement element : typeElement.getAsJsonArray()) {
                    if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
                        typeNames.add(element.getAsString());
                    }
                }
                if (!typeNames.isEmpty()) {
                    return String.join("|", typeNames);
                }
            }
        }

        if (propertyObject.has("enum")) {
            return "enum";
        }
        if (propertyObject.has("properties")) {
            return "object";
        }
        if (propertyObject.has("items")) {
            return "array";
        }
        return "";
    }

    private static String extractPropertyDescription(JsonElement propertySchema) {
        if (propertySchema == null || !propertySchema.isJsonObject()) {
            return "";
        }

        JsonElement descriptionElement = propertySchema.getAsJsonObject().get("description");
        if (descriptionElement == null || !descriptionElement.isJsonPrimitive()
                || !descriptionElement.getAsJsonPrimitive().isString()) {
            return "";
        }

        String description = descriptionElement.getAsString();
        return description == null ? "" : description.trim();
    }

    public record McpRemoteTool(
            String name,
            Optional<String> description,
            JsonElement inputSchema,
            Optional<Boolean> readOnlyHint
    ) {
        public McpRemoteTool {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("MCP remote tool name is required.");
            }
            description = description == null ? Optional.empty() : description.filter(value -> !value.isBlank());
            inputSchema = inputSchema == null ? null : inputSchema.deepCopy();
            readOnlyHint = readOnlyHint == null ? Optional.empty() : readOnlyHint;
        }
    }

    public record McpProjectedTool(
            String serverAlias,
            String remoteToolName,
            String qualifiedToolName,
            Optional<Boolean> readOnlyHint,
            AgentTool toolSpec
    ) {
        public McpProjectedTool {
            if (serverAlias == null || serverAlias.isBlank()) {
                throw new IllegalArgumentException("MCP server alias is required.");
            }
            if (remoteToolName == null || remoteToolName.isBlank()) {
                throw new IllegalArgumentException("MCP remote tool name is required.");
            }
            if (qualifiedToolName == null || qualifiedToolName.isBlank()) {
                throw new IllegalArgumentException("Qualified MCP tool name is required.");
            }
            readOnlyHint = readOnlyHint == null ? Optional.empty() : readOnlyHint;
            toolSpec = Objects.requireNonNull(toolSpec, "toolSpec");
        }
    }
}

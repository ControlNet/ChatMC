package space.controlnet.chatmc.core.tools;

import java.util.List;
import java.util.Optional;

/**
 * Describes a tool for prompt rendering and UI formatting.
 */
public interface AgentTool {
    String name();

    String description();

    String argsSchema();

    List<String> argsDescription();

    String resultSchema();

    List<String> resultDescription();

    List<String> examples();

    ToolRender render(ToolPayload payload);

    default Optional<String> descriptionOptional() {
        return optionalText(description());
    }

    default Optional<String> argsSchemaOptional() {
        return optionalText(argsSchema());
    }

    default List<String> argsDescriptionOptional() {
        return optionalLines(argsDescription());
    }

    default Optional<String> resultSchemaOptional() {
        return optionalText(resultSchema());
    }

    default List<String> resultDescriptionOptional() {
        return optionalLines(resultDescription());
    }

    default List<String> examplesOptional() {
        return optionalLines(examples());
    }

    private static Optional<String> optionalText(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    private static List<String> optionalLines(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .toList();
    }
}

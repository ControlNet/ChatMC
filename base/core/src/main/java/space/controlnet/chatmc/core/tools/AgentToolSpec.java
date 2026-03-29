package space.controlnet.chatmc.core.tools;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

public record AgentToolSpec(
        String name,
        String description,
        String argsSchema,
        List<String> argsDescription,
        String resultSchema,
        List<String> resultDescription,
        List<String> examples,
        Function<ToolPayload, ToolRender> renderer
) implements AgentTool {
    public AgentToolSpec {
        name = Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        argsSchema = argsSchema == null ? "" : argsSchema;
        argsDescription = argsDescription == null ? List.of() : List.copyOf(argsDescription);
        resultSchema = resultSchema == null ? "" : resultSchema;
        resultDescription = resultDescription == null ? List.of() : List.copyOf(resultDescription);
        examples = examples == null ? List.of() : List.copyOf(examples);
    }

    @Override
    public ToolRender render(ToolPayload payload) {
        return renderer == null ? null : renderer.apply(payload);
    }

    public static AgentToolSpec metadataOnly(
            String name,
            String description,
            String argsSchema,
            List<String> argsDescription,
            String resultSchema,
            List<String> resultDescription,
            List<String> examples
    ) {
        return new AgentToolSpec(name, description, argsSchema, argsDescription, resultSchema,
                resultDescription, examples, null);
    }
}

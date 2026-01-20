package space.controlnet.chatmc.core.agent;

import java.util.Map;

public record PromptContext(
        PromptId promptId,
        String effectiveLocale,
        Map<String, String> variables
) {
}

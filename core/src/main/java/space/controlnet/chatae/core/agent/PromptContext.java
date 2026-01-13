package space.controlnet.chatae.core.agent;

import java.util.Map;

public record PromptContext(
        PromptId promptId,
        String effectiveLocale,
        Map<String, String> variables
) {
}

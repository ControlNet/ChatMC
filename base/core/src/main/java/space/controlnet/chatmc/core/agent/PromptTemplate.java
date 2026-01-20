package space.controlnet.chatmc.core.agent;

import java.util.Map;

public final class PromptTemplate {
    private PromptTemplate() {
    }

    public static String render(String template, Map<String, String> variables) {
        if (template == null || template.isBlank()) {
            return "";
        }
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            String key = "{{" + entry.getKey() + "}}";
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace(key, value);
        }
        return result;
    }
}

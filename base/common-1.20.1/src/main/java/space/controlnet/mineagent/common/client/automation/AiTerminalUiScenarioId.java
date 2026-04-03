package space.controlnet.mineagent.common.client.automation;

import java.util.Locale;

public enum AiTerminalUiScenarioId {
    EMPTY,
    CHAT_SHORT,
    SUGGESTIONS_VISIBLE,
    PROPOSAL_PENDING,
    EXECUTING,
    ERROR_STATE,
    HTTP_RESULT,
    SESSION_LIST_DENSE;

    public String externalName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static AiTerminalUiScenarioId parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return EMPTY;
        }
        String normalized = raw.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        return valueOf(normalized);
    }
}

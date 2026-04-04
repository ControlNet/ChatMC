package space.controlnet.mineagent.common.client.automation;

import space.controlnet.mineagent.common.client.screen.components.ItemSuggestion;
import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.session.SessionSummary;

import java.util.List;

public record AiTerminalUiPreviewState(
        SessionSnapshot snapshot,
        List<SessionSummary> sessionSummaries,
        String aiLocaleOverride,
        boolean sessionsOpen,
        String inputText,
        List<ItemSuggestion> itemSuggestions,
        int selectedSuggestionIndex,
        boolean statusScreenOpen
) {
    public AiTerminalUiPreviewState {
        sessionSummaries = sessionSummaries == null ? List.of() : List.copyOf(sessionSummaries);
        aiLocaleOverride = aiLocaleOverride == null ? "" : aiLocaleOverride;
        inputText = inputText == null ? "" : inputText;
        itemSuggestions = itemSuggestions == null ? List.of() : List.copyOf(itemSuggestions);
    }
}

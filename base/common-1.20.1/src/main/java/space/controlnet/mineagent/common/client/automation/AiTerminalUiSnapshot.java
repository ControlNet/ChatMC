package space.controlnet.mineagent.common.client.automation;

public record AiTerminalUiSnapshot(
        boolean screenOpen,
        String screenClassName,
        String statusText,
        boolean sendButtonActive,
        boolean approveButtonVisible,
        boolean denyButtonVisible,
        boolean sessionsOpen,
        int sessionSummaryCount,
        int visibleSessionRowCount,
        boolean suggestionsVisible,
        int suggestionCount,
        int selectedSuggestionIndex,
        String inputText,
        int messageCount,
        int wrappedLineCount,
        boolean proposalVisible
) {
}

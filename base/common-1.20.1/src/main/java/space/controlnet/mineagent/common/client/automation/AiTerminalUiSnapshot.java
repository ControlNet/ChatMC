package space.controlnet.mineagent.common.client.automation;

import java.util.List;

public record AiTerminalUiSnapshot(
        boolean screenOpen,
        String screenClassName,
        String statusText,
        boolean statusButtonVisible,
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
        int inputTokenCount,
        int messageCount,
        int wrappedLineCount,
        boolean proposalVisible,
        int toolSectionCount,
        List<String> toolSectionLabels,
        int builtInToolCount,
        int extToolCount,
        int mcpToolCount,
        List<String> builtInToolNames,
        List<String> extToolNames,
        List<String> mcpToolNames
) {
}

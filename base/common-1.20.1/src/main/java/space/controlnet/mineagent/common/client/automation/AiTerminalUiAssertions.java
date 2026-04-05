package space.controlnet.mineagent.common.client.automation;

public final class AiTerminalUiAssertions {
    private AiTerminalUiAssertions() {
    }

    public static void assertMatches(AiTerminalUiScenarioId scenarioId, AiTerminalUiSnapshot snapshot) {
        require("ui/common/screen-open", snapshot.screenOpen());

        switch (scenarioId) {
            case EMPTY -> {
                requireEquals("ui/empty/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/empty/status", "IDLE", snapshot.statusText());
                requireEquals("ui/empty/message-count", 0, snapshot.messageCount());
                requireEquals("ui/empty/input", "", snapshot.inputText());
                require("ui/empty/send-active", snapshot.sendButtonActive());
                require("ui/empty/no-proposal", !snapshot.proposalVisible());
            }
            case CHAT_SHORT -> {
                requireEquals("ui/chat-short/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/chat-short/status", "DONE", snapshot.statusText());
                requireAtLeast("ui/chat-short/messages", 3, snapshot.messageCount());
                requireAtLeast("ui/chat-short/wrapped-lines", 3, snapshot.wrappedLineCount());
                require("ui/chat-short/send-active", snapshot.sendButtonActive());
            }
            case SUGGESTIONS_VISIBLE -> {
                requireEquals("ui/suggestions/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/suggestions/status", "IDLE", snapshot.statusText());
                require("ui/suggestions/popup-visible", snapshot.suggestionsVisible());
                requireAtLeast("ui/suggestions/count", 3, snapshot.suggestionCount());
                requireEquals("ui/suggestions/selected-index", 1, snapshot.selectedSuggestionIndex());
                require("ui/suggestions/input-query", snapshot.inputText().contains("@dia"));
            }
            case PROPOSAL_PENDING -> {
                requireEquals("ui/proposal/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/proposal/status", "WAIT_APPROVAL", snapshot.statusText());
                require("ui/proposal/visible", snapshot.proposalVisible());
                require("ui/proposal/approve-visible", snapshot.approveButtonVisible());
                require("ui/proposal/deny-visible", snapshot.denyButtonVisible());
                require("ui/proposal/send-disabled", !snapshot.sendButtonActive());
            }
            case EXECUTING -> {
                requireEquals("ui/executing/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/executing/status", "EXECUTING", snapshot.statusText());
                require("ui/executing/send-disabled", !snapshot.sendButtonActive());
                requireAtLeast("ui/executing/messages", 2, snapshot.messageCount());
            }
            case ERROR_STATE -> {
                requireEquals("ui/error/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/error/status", "FAILED", snapshot.statusText());
                require("ui/error/send-active", snapshot.sendButtonActive());
                requireAtLeast("ui/error/messages", 2, snapshot.messageCount());
            }
            case HTTP_RESULT -> {
                requireEquals("ui/http/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                requireEquals("ui/http/status", "DONE", snapshot.statusText());
                requireAtLeast("ui/http/messages", 2, snapshot.messageCount());
                requireAtLeast("ui/http/wrapped-lines", 4, snapshot.wrappedLineCount());
            }
            case SESSION_LIST_DENSE -> {
                requireEquals("ui/session-dense/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                require("ui/session-dense/panel-open", snapshot.sessionsOpen());
                requireAtLeast("ui/session-dense/session-count", 7, snapshot.sessionSummaryCount());
                requireAtLeast("ui/session-dense/visible-rows", 4, snapshot.visibleSessionRowCount());
            }
            case STATUS_BUTTON -> {
                requireEquals("ui/status-button/screen-class", "AiTerminalScreen", snapshot.screenClassName());
                require("ui/status-button/visible", snapshot.statusButtonVisible());
            }
            case STATUS_PANEL -> {
                requireEquals("ui/status-panel/screen-class", "AiTerminalStatusScreen", snapshot.screenClassName());
                requireAtLeast("ui/status-panel/section-count", 3, snapshot.toolSectionCount());
                require("ui/status-panel/built-in-section", snapshot.toolSectionLabels().contains("Built-in"));
                require("ui/status-panel/ext-mod-section", snapshot.toolSectionLabels().contains("mineagentae"));
                require("ui/status-panel/mcp-section", snapshot.toolSectionLabels().contains("MCP"));
                requireAtLeast("ui/status-panel/built-in-count", 1, snapshot.builtInToolCount());
                requireAtLeast("ui/status-panel/ext-count", 1, snapshot.extToolCount());
                requireAtLeast("ui/status-panel/mcp-count", 1, snapshot.mcpToolCount());
                require("ui/status-panel/total-count", snapshot.builtInToolCount() + snapshot.extToolCount() + snapshot.mcpToolCount() >= 3);
                require("ui/status-panel/built-in-http", snapshot.builtInToolNames().contains("http"));
                require("ui/status-panel/built-in-find-recipes", snapshot.builtInToolNames().contains("mc.find_recipes"));
                require("ui/status-panel/ext-ae-list-items", snapshot.extToolNames().contains("ae.list_items"));
                require("ui/status-panel/mcp-statusdocs", snapshot.mcpToolNames().contains("mcp.statusdocs.search"));
            }
            case STATUS_PANEL_SCROLLED -> {
                requireEquals("ui/status-panel-scrolled/screen-class", "AiTerminalStatusScreen", snapshot.screenClassName());
                requireAtLeast("ui/status-panel-scrolled/section-count", 3, snapshot.toolSectionCount());
                requireAtLeast("ui/status-panel-scrolled/mcp-count", 5, snapshot.mcpToolCount());
                require("ui/status-panel-scrolled/mcp-statusdocs", snapshot.mcpToolNames().contains("mcp.statusdocs.search"));
            }
        }
    }

    private static void require(String assertionName, boolean condition) {
        if (!condition) {
            throw new AssertionError(assertionName);
        }
    }

    private static void requireAtLeast(String assertionName, int minimum, int actual) {
        if (actual < minimum) {
            throw new AssertionError(assertionName + " -> expected >= " + minimum + ", got " + actual);
        }
    }

    private static void requireEquals(String assertionName, Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError(assertionName + " -> expected " + expected + ", got " + actual);
        }
    }
}

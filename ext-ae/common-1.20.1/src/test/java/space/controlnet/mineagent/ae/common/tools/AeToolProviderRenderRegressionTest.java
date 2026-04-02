package space.controlnet.mineagent.ae.common.tools;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.ae.core.terminal.AeTerminalContext;
import space.controlnet.mineagent.ae.core.terminal.AiTerminalData;
import space.controlnet.mineagent.core.tools.ToolCall;
import space.controlnet.mineagent.core.tools.ToolOutcome;
import space.controlnet.mineagent.core.tools.ToolResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AeToolProviderRenderRegressionTest {
    @Test
    void task18_aeProvider_invalidAndUnknownTools_returnStableErrors() {
        AeToolProvider provider = new AeToolProvider();

        ToolOutcome missingCall = provider.execute(Optional.empty(), null, true);
        ToolOutcome missingToolName = provider.execute(Optional.empty(), new ToolCall(null, "{}"), true);
        ToolOutcome unknownTool = provider.execute(Optional.empty(), new ToolCall("ae.unknown", "{}"), true);

        assertErrorCode("task18/ae-provider/missing-call", missingCall, "invalid_tool");
        assertErrorCode("task18/ae-provider/missing-tool-name", missingToolName, "invalid_tool");
        assertErrorCode("task18/ae-provider/unknown-tool", unknownTool, "unknown_tool");
    }

    @Test
    void task18_aeProvider_readOnlyAndArgumentFailures_areStable() {
        AeToolProvider provider = new AeToolProvider();

        ToolOutcome autoApprovedReadOnly = provider.execute(Optional.empty(),
                new ToolCall("ae.list_craftables", "{\"query\":\"fluix\",\"limit\":3}"), false);
        ToolOutcome missingListItemsArgs = provider.execute(Optional.empty(),
                new ToolCall("ae.list_items", ""), true);
        ToolOutcome missingListCraftablesArgs = provider.execute(Optional.empty(),
                new ToolCall("ae.list_craftables", ""), true);
        ToolOutcome invalidRequestCount = provider.execute(Optional.empty(),
                new ToolCall("ae.request_craft", "{\"itemId\":\"ae2:controller\",\"count\":10001}"), true);
        ToolOutcome missingCancelArgs = provider.execute(Optional.empty(),
                new ToolCall("ae.job_cancel", "{}"), true);

        assertErrorCode("task18/ae-provider/auto-approved-read-only", autoApprovedReadOnly, "no_terminal");
        assertErrorCode("task18/ae-provider/missing-list-items-args", missingListItemsArgs, "invalid_args");
        assertErrorCode("task18/ae-provider/missing-list-craftables-args", missingListCraftablesArgs, "invalid_args");
        assertErrorCode("task18/ae-provider/invalid-request-count", invalidRequestCount, "invalid_count");
        assertErrorCode("task18/ae-provider/missing-cancel-args", missingCancelArgs, "invalid_args");
    }

    @Test
    void task25_aeProvider_proposalsArgumentValidationAndTerminalExecution_areStable() {
        AeToolProvider provider = new AeToolProvider();

        ToolOutcome craftProposal = provider.execute(Optional.empty(),
                new ToolCall("ae.request_craft", "{\"itemId\":\"ae2:controller\",\"count\":1}"), false);
        assertTrue("task25/ae-provider/request-proposal", craftProposal.hasProposal());
        assertTrue("task25/ae-provider/request-summary",
                craftProposal.proposal() != null && craftProposal.proposal().summary().contains("Craft 1 ae2:controller"));

        ToolOutcome cancelProposal = provider.execute(Optional.empty(),
                new ToolCall("ae.job_cancel", "{\"jobId\":\"job-7\"}"), false);
        assertTrue("task25/ae-provider/cancel-proposal", cancelProposal.hasProposal());
        assertTrue("task25/ae-provider/cancel-summary",
                cancelProposal.proposal() != null && cancelProposal.proposal().summary().contains("Cancel job job-7"));

        ToolOutcome malformedArgs = provider.execute(Optional.empty(), new ToolCall("ae.list_items", "{"), true);
        assertErrorCode("task25/ae-provider/malformed-args", malformedArgs, "invalid_args");

        ToolOutcome missingItemId = provider.execute(Optional.empty(),
                new ToolCall("ae.simulate_craft", "{\"itemId\":\"\",\"count\":1}"), true);
        assertErrorCode("task25/ae-provider/missing-item-id", missingItemId, "invalid_args");

        ToolOutcome missingJobId = provider.execute(Optional.empty(),
                new ToolCall("ae.job_status", "{\"jobId\":\"\"}"), true);
        assertErrorCode("task25/ae-provider/missing-job-id", missingJobId, "invalid_args");

        FakeTerminalContext terminal = new FakeTerminalContext();
        Optional<space.controlnet.mineagent.core.terminal.TerminalContext> terminalRef = Optional.of(terminal);

        ToolOutcome listItems = provider.execute(terminalRef,
                new ToolCall("ae.list_items", "{\"query\":\"fluix\",\"craftableOnly\":false,\"limit\":5,\"pageToken\":\"p1\"}"), true);
        assertSuccessPayloadContains("task25/ae-provider/list-items", listItems, "fluix");

        ToolOutcome listCraftables = provider.execute(terminalRef,
                new ToolCall("ae.list_craftables", "{\"query\":\"processor\",\"limit\":3,\"pageToken\":\"p2\"}"), true);
        assertSuccessPayloadContains("task25/ae-provider/list-craftables", listCraftables, "processor");

        ToolOutcome simulate = provider.execute(terminalRef,
                new ToolCall("ae.simulate_craft", "{\"itemId\":\"ae2:controller\",\"count\":2}"), true);
        assertSuccessPayloadContains("task25/ae-provider/simulate", simulate, "sim-job");

        ToolOutcome request = provider.execute(terminalRef,
                new ToolCall("ae.request_craft", "{\"itemId\":\"ae2:controller\",\"count\":2,\"cpuName\":\"cpu-a\"}"), true);
        assertSuccessPayloadContains("task25/ae-provider/request", request, "req-job");

        ToolOutcome status = provider.execute(terminalRef,
                new ToolCall("ae.job_status", "{\"jobId\":\"job-3\"}"), true);
        assertSuccessPayloadContains("task25/ae-provider/status", status, "job-3");

        ToolOutcome cancel = provider.execute(terminalRef,
                new ToolCall("ae.job_cancel", "{\"jobId\":\"job-3\"}"), true);
        assertSuccessPayloadContains("task25/ae-provider/cancel", cancel, "cancelled");

        assertEquals("task25/ae-provider/terminal-call-count", 6, terminal.calls.size());
    }

    private static ToolResult requireResult(String assertionName, ToolOutcome outcome) {
        ToolOutcome nonNullOutcome = requireNonNull(assertionName + "/outcome", outcome);
        return requireNonNull(assertionName + "/result", nonNullOutcome.result());
    }

    private static void assertErrorCode(String assertionName, ToolOutcome outcome, String expectedCode) {
        ToolResult result = requireResult(assertionName, outcome);
        assertTrue(assertionName + "/must-fail", !result.success());
        assertEquals(assertionName + "/code", expectedCode, requireNonNull(assertionName + "/error", result.error()).code());
    }

    private static void assertSuccessPayloadContains(String assertionName, ToolOutcome outcome, String expectedFragment) {
        ToolResult result = requireResult(assertionName, outcome);
        assertTrue(assertionName + "/must-succeed", result.success());
        String payload = requireNonNull(assertionName + "/payload", result.payloadJson());
        assertTrue(assertionName + "/payload-contains", payload.contains(expectedFragment));
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static <T> T requireNonNull(String assertionName, T value) {
        if (value != null) {
            return value;
        }
        throw new AssertionError(assertionName + " -> expected non-null");
    }

    private static void assertEquals(String assertionName, Object expected, Object actual) {
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected: " + expected + ", actual: " + actual);
    }

    private static final class FakeTerminalContext implements AeTerminalContext {
        private final List<String> calls = new ArrayList<>();

        @Override
        public AiTerminalData.AeListResult listItems(String query, boolean craftableOnly, int limit, String pageToken) {
            calls.add("listItems");
            return new AiTerminalData.AeListResult(
                    List.of(new AiTerminalData.AeEntry("ae2:" + query, limit, craftableOnly)),
                    Optional.ofNullable(pageToken),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeListResult listCraftables(String query, int limit, String pageToken) {
            calls.add("listCraftables");
            return new AiTerminalData.AeListResult(
                    List.of(new AiTerminalData.AeEntry("ae2:" + query, limit, true)),
                    Optional.ofNullable(pageToken),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeCraftSimulation simulateCraft(String itemId, long count) {
            calls.add("simulateCraft");
            return new AiTerminalData.AeCraftSimulation(
                    "sim-job",
                    "queued",
                    List.of(new AiTerminalData.AePlanItem(itemId, count)),
                    Optional.empty()
            );
        }

        @Override
        public AiTerminalData.AeCraftRequest requestCraft(String itemId, long count, String cpuName) {
            calls.add("requestCraft");
            return new AiTerminalData.AeCraftRequest("req-job", "requested", Optional.empty());
        }

        @Override
        public AiTerminalData.AeJobStatus jobStatus(String jobId) {
            calls.add("jobStatus");
            return new AiTerminalData.AeJobStatus(jobId, "running", List.of(), Optional.empty());
        }

        @Override
        public AiTerminalData.AeJobStatus cancelJob(String jobId) {
            calls.add("cancelJob");
            return new AiTerminalData.AeJobStatus(jobId, "cancelled", List.of(), Optional.empty());
        }
    }
}

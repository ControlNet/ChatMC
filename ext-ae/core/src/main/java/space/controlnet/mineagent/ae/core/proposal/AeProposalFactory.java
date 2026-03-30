package space.controlnet.mineagent.ae.core.proposal;

import com.google.gson.Gson;
import space.controlnet.mineagent.ae.core.tools.AeToolArgs;
import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.proposal.Proposal;
import space.controlnet.mineagent.core.proposal.ProposalDetails;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.util.List;
import java.util.UUID;

public final class AeProposalFactory {
    private static final Gson GSON = new Gson();

    private AeProposalFactory() {
    }

    public static Proposal build(RiskLevel risk, ToolCall call) {
        ProposalDetails details = ProposalDetails.empty();
        String summary = call.toolName();

        if ("ae.request_craft".equals(call.toolName())) {
            var args = GSON.fromJson(call.argsJson(), AeToolArgs.AeCraftArgs.class);
            String itemId = args == null ? "" : args.itemId();
            long count = args == null ? 0 : args.count();
            summary = "Craft " + count + " " + itemId;
            String note = args != null && args.cpuName() != null && !args.cpuName().isBlank()
                    ? "CPU hint: " + args.cpuName() + ". Feasibility: unknown (run ae.simulate_craft). Cancel after submit with ae.job_cancel <jobId>."
                    : "Feasibility: unknown (run ae.simulate_craft). Cancel after submit with ae.job_cancel <jobId>.";
            details = new ProposalDetails("Craft", itemId, count, List.of(), note);
        } else if ("ae.job_cancel".equals(call.toolName())) {
            var args = GSON.fromJson(call.argsJson(), AeToolArgs.AeJobArgs.class);
            String jobId = args == null ? "" : args.jobId();
            summary = "Cancel job " + jobId;
            details = new ProposalDetails("Cancel job", jobId, 0L, List.of(), "Stops an active crafting job.");
        }

        return new Proposal(UUID.randomUUID().toString(), risk, summary, call, System.currentTimeMillis(), details);
    }
}

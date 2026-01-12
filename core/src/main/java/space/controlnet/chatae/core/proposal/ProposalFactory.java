package space.controlnet.chatae.core.proposal;

import com.google.gson.Gson;
import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.tools.ToolArgs;
import space.controlnet.chatae.core.tools.ToolCall;

import java.util.List;
import java.util.UUID;

public final class ProposalFactory {
    private static final Gson GSON = new Gson();

    private ProposalFactory() {
    }

    public static Proposal build(RiskLevel risk, ToolCall call) {
        ProposalDetails details = ProposalDetails.empty();
        String summary = call.toolName();

        if ("ae2.request_craft".equals(call.toolName())) {
            var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2CraftArgs.class);
            String itemId = args == null ? "" : args.itemId();
            long count = args == null ? 0 : args.count();
            summary = "Craft " + count + " " + itemId;
            String note = args != null && args.cpuName() != null && !args.cpuName().isBlank()
                    ? "CPU hint: " + args.cpuName() + ". Feasibility: unknown (run ae2.simulate_craft). Cancel after submit with ae2.job_cancel <jobId>."
                    : "Feasibility: unknown (run ae2.simulate_craft). Cancel after submit with ae2.job_cancel <jobId>.";
            details = new ProposalDetails("Craft", itemId, count, List.of(), note);
        } else if ("ae2.job_cancel".equals(call.toolName())) {
            var args = GSON.fromJson(call.argsJson(), ToolArgs.Ae2JobArgs.class);
            String jobId = args == null ? "" : args.jobId();
            summary = "Cancel job " + jobId;
            details = new ProposalDetails("Cancel job", jobId, 0L, List.of(), "Stops an active crafting job.");
        }

        return new Proposal(UUID.randomUUID().toString(), risk, summary, call, System.currentTimeMillis(), details);
    }
}

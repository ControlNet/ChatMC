package space.controlnet.mineagent.core.tools;

import space.controlnet.mineagent.core.proposal.Proposal;

public record ToolOutcome(ToolResult result, Proposal proposal) {
    public static ToolOutcome result(ToolResult result) {
        return new ToolOutcome(result, null);
    }

    public static ToolOutcome proposal(Proposal proposal) {
        return new ToolOutcome(null, proposal);
    }

    public boolean hasProposal() {
        return proposal != null;
    }
}

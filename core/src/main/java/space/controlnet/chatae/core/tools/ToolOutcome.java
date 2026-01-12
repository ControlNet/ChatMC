package space.controlnet.chatae.core.tools;

import space.controlnet.chatae.core.proposal.Proposal;

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

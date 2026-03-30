package space.controlnet.mineagent.core.proposal;

import space.controlnet.mineagent.core.policy.RiskLevel;
import space.controlnet.mineagent.core.tools.ToolCall;

import java.io.Serializable;

public record Proposal(
        String id,
        RiskLevel riskLevel,
        String summary,
        ToolCall toolCall,
        long createdAtMillis,
        ProposalDetails details
) implements Serializable {
    public Proposal {
        details = details == null ? ProposalDetails.empty() : details;
    }
}

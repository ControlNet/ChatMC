package space.controlnet.chatmc.core.proposal;

import space.controlnet.chatmc.core.policy.RiskLevel;
import space.controlnet.chatmc.core.tools.ToolCall;

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

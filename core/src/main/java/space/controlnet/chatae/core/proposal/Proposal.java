package space.controlnet.chatae.core.proposal;

import space.controlnet.chatae.core.policy.RiskLevel;
import space.controlnet.chatae.core.tools.ToolCall;

public record Proposal(
        String id,
        RiskLevel riskLevel,
        String summary,
        ToolCall toolCall,
        long createdAtMillis
) {
}

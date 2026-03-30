package space.controlnet.mineagent.core.net.c2s;

import space.controlnet.mineagent.core.proposal.ApprovalDecision;

public record C2SApprovalDecisionPacket(int protocolVersion, String proposalId, ApprovalDecision decision) {
}

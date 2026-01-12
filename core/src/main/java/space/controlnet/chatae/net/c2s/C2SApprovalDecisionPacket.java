package space.controlnet.chatae.net.c2s;

import space.controlnet.chatae.core.proposal.ApprovalDecision;

public record C2SApprovalDecisionPacket(int protocolVersion, String proposalId, ApprovalDecision decision) {
}

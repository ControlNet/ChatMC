package space.controlnet.chatae.net.c2s;

import space.controlnet.chatae.core.proposal.ApprovalDecision;

public record C2SApprovalDecisionPacket(String proposalId, ApprovalDecision decision) {
}

package space.controlnet.chatmc.core.net.c2s;

import space.controlnet.chatmc.core.proposal.ApprovalDecision;

public record C2SApprovalDecisionPacket(int protocolVersion, String proposalId, ApprovalDecision decision) {
}

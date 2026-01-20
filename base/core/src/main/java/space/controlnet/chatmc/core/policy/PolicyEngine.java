package space.controlnet.chatmc.core.policy;

import space.controlnet.chatmc.core.session.SessionSnapshot;
import space.controlnet.chatmc.core.tools.ToolCall;

public interface PolicyEngine {
    PolicyDecision decide(ToolCall toolCall, RiskLevel riskLevel, SessionSnapshot session);
}

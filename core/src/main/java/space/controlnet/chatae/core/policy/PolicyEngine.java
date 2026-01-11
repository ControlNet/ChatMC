package space.controlnet.chatae.core.policy;

import space.controlnet.chatae.core.session.SessionSnapshot;
import space.controlnet.chatae.core.tools.ToolCall;

public interface PolicyEngine {
    PolicyDecision decide(ToolCall toolCall, RiskLevel riskLevel, SessionSnapshot session);
}

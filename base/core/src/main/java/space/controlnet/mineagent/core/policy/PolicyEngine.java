package space.controlnet.mineagent.core.policy;

import space.controlnet.mineagent.core.session.SessionSnapshot;
import space.controlnet.mineagent.core.tools.ToolCall;

public interface PolicyEngine {
    PolicyDecision decide(ToolCall toolCall, RiskLevel riskLevel, SessionSnapshot session);
}

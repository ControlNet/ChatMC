package space.controlnet.mineagent.core.net.s2c;

import space.controlnet.mineagent.core.session.SessionSummary;

import java.util.List;

public record S2CSessionListPacket(int protocolVersion, List<SessionSummary> sessions) {
}

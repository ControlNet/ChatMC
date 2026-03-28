package space.controlnet.chatmc.core.net.s2c;

import space.controlnet.chatmc.core.session.SessionSummary;

import java.util.List;

public record S2CSessionListPacket(int protocolVersion, List<SessionSummary> sessions) {
}

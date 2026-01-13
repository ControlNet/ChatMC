package space.controlnet.chatae.core.net.s2c;

import space.controlnet.chatae.core.session.SessionSummary;

import java.util.List;

public record S2CSessionListPacket(int protocolVersion, List<SessionSummary> sessions) {
}

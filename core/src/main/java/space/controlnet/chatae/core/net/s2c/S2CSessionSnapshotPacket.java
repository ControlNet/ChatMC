package space.controlnet.chatae.core.net.s2c;

import space.controlnet.chatae.core.session.SessionSnapshot;

public record S2CSessionSnapshotPacket(int protocolVersion, SessionSnapshot snapshot) {
}

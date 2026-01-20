package space.controlnet.chatmc.core.net.s2c;

import space.controlnet.chatmc.core.session.SessionSnapshot;

public record S2CSessionSnapshotPacket(int protocolVersion, SessionSnapshot snapshot) {
}

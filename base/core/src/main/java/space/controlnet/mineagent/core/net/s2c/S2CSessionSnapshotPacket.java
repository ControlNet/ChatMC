package space.controlnet.mineagent.core.net.s2c;

import space.controlnet.mineagent.core.session.SessionSnapshot;

public record S2CSessionSnapshotPacket(int protocolVersion, SessionSnapshot snapshot) {
}

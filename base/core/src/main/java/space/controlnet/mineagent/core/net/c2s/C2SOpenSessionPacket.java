package space.controlnet.mineagent.core.net.c2s;

import java.util.UUID;

public record C2SOpenSessionPacket(int protocolVersion, UUID sessionId) {
}

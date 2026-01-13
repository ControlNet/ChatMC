package space.controlnet.chatae.core.net.c2s;

import java.util.UUID;

public record C2SDeleteSessionPacket(int protocolVersion, UUID sessionId) {
}

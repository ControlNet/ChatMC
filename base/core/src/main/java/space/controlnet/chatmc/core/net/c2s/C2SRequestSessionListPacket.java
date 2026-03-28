package space.controlnet.chatmc.core.net.c2s;

import space.controlnet.chatmc.core.session.SessionListScope;

public record C2SRequestSessionListPacket(int protocolVersion, SessionListScope scope) {
}

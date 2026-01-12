package space.controlnet.chatae.net.c2s;

import space.controlnet.chatae.core.session.SessionListScope;

public record C2SRequestSessionListPacket(int protocolVersion, SessionListScope scope) {
}

package space.controlnet.mineagent.core.net.c2s;

import space.controlnet.mineagent.core.session.SessionListScope;

public record C2SRequestSessionListPacket(int protocolVersion, SessionListScope scope) {
}

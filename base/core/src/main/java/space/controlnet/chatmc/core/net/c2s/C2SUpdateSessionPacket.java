package space.controlnet.chatmc.core.net.c2s;

import space.controlnet.chatmc.core.session.SessionVisibility;

import java.util.Optional;
import java.util.UUID;

public record C2SUpdateSessionPacket(int protocolVersion,
                                     UUID sessionId,
                                     Optional<String> title,
                                     Optional<SessionVisibility> visibility) {
    public C2SUpdateSessionPacket {
        title = title == null ? Optional.empty() : title;
        visibility = visibility == null ? Optional.empty() : visibility;
    }
}

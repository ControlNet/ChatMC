package space.controlnet.chatmc.core.session;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PersistedSessions(
        int version,
        List<SessionSnapshot> sessions,
        Map<UUID, UUID> activeSessionByPlayer
) {
    public PersistedSessions {
        sessions = List.copyOf(sessions);
        activeSessionByPlayer = Map.copyOf(activeSessionByPlayer);
    }
}

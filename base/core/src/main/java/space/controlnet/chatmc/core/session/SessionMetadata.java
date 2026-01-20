package space.controlnet.chatmc.core.session;

import java.util.Optional;
import java.util.UUID;

public record SessionMetadata(
        UUID sessionId,
        UUID ownerId,
        String ownerName,
        SessionVisibility visibility,
        Optional<String> teamId,
        String title,
        long createdAtMillis,
        long lastActiveMillis
) {
    public SessionMetadata {
        teamId = teamId == null ? Optional.empty() : teamId;
    }
}

package space.controlnet.chatae.core.session;

import java.util.Optional;
import java.util.UUID;

public record SessionSummary(
        UUID sessionId,
        UUID ownerId,
        String ownerName,
        SessionVisibility visibility,
        Optional<String> teamId,
        String title,
        long createdAtMillis,
        long lastActiveMillis
) {
    public SessionSummary {
        teamId = teamId == null ? Optional.empty() : teamId;
    }
}

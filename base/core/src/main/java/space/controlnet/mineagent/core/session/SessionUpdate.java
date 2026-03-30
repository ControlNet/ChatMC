package space.controlnet.mineagent.core.session;

import java.util.Optional;

public record SessionUpdate(Optional<String> title, Optional<SessionVisibility> visibility) {
    public SessionUpdate {
        title = title == null ? Optional.empty() : title;
        visibility = visibility == null ? Optional.empty() : visibility;
    }
}

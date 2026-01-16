package space.controlnet.chatae.core.session;

import java.io.Serializable;
import java.util.Optional;

public record TerminalBinding(
        String dimensionId,
        int x,
        int y,
        int z,
        Optional<String> side
) implements Serializable {
    public TerminalBinding {
        side = side == null ? Optional.empty() : side;
    }
}

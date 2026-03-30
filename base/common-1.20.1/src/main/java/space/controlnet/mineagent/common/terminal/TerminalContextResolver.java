package space.controlnet.mineagent.common.terminal;

import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.terminal.TerminalContext;

import java.util.Optional;

/**
 * Resolves terminal contexts for tools that require terminal access.
 */
public interface TerminalContextResolver {
    Optional<TerminalContext> fromPlayer(ServerPlayer player);

    Optional<TerminalContext> fromPlayerAtBinding(ServerPlayer player, TerminalBinding binding);
}

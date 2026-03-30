package space.controlnet.mineagent.common.terminal;

import net.minecraft.server.level.ServerPlayer;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.terminal.TerminalContext;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Registry for terminal context resolvers supplied by extensions.
 */
public final class TerminalContextRegistry {
    private static final AtomicReference<TerminalContextResolver> RESOLVER = new AtomicReference<>();

    private TerminalContextRegistry() {
    }

    public static void register(TerminalContextResolver resolver) {
        RESOLVER.set(resolver);
    }

    public static Optional<TerminalContext> fromPlayer(ServerPlayer player) {
        TerminalContextResolver resolver = RESOLVER.get();
        if (resolver == null) {
            return Optional.empty();
        }
        return resolver.fromPlayer(player);
    }

    public static Optional<TerminalContext> fromPlayerAtBinding(ServerPlayer player, TerminalBinding binding) {
        TerminalContextResolver resolver = RESOLVER.get();
        if (resolver == null) {
            return Optional.empty();
        }
        return resolver.fromPlayerAtBinding(player, binding);
    }
}

package space.controlnet.mineagent.common.terminal;

import org.junit.jupiter.api.Test;
import space.controlnet.mineagent.core.session.TerminalBinding;
import space.controlnet.mineagent.core.terminal.TerminalContext;

import java.util.Optional;

public final class TerminalContextRegistryRegressionTest {
    @Test
    void task25_terminalContextRegistry_defaultAndRegisteredResolverPaths_areStable() {
        TerminalContextRegistry.register(null);
        assertTrue("task25/terminal-registry/default-empty-player", TerminalContextRegistry.fromPlayer(null).isEmpty());
        assertTrue("task25/terminal-registry/default-empty-binding",
                TerminalContextRegistry.fromPlayerAtBinding(null,
                        new TerminalBinding("minecraft:overworld", 0, 64, 0, Optional.empty())).isEmpty());

        DummyContext expectedContext = new DummyContext();
        TerminalBinding binding = new TerminalBinding("minecraft:overworld", 1, 70, 2, Optional.of("NORTH"));
        TerminalContextResolver resolver = new TerminalContextResolver() {
            @Override
            public Optional<TerminalContext> fromPlayer(net.minecraft.server.level.ServerPlayer player) {
                return Optional.of(expectedContext);
            }

            @Override
            public Optional<TerminalContext> fromPlayerAtBinding(net.minecraft.server.level.ServerPlayer player, TerminalBinding fromBinding) {
                if (fromBinding.x() == binding.x() && fromBinding.y() == binding.y() && fromBinding.z() == binding.z()) {
                    return Optional.of(expectedContext);
                }
                return Optional.empty();
            }
        };

        TerminalContextRegistry.register(resolver);

        Optional<TerminalContext> fromPlayer = TerminalContextRegistry.fromPlayer(null);
        Optional<TerminalContext> fromBinding = TerminalContextRegistry.fromPlayerAtBinding(null, binding);

        assertTrue("task25/terminal-registry/from-player-present", fromPlayer.isPresent());
        assertTrue("task25/terminal-registry/from-binding-present", fromBinding.isPresent());
        assertTrue("task25/terminal-registry/context-same-player", fromPlayer.get() == expectedContext);
        assertTrue("task25/terminal-registry/context-same-binding", fromBinding.get() == expectedContext);

        TerminalContextRegistry.register(null);
    }

    private static void assertTrue(String assertionName, boolean condition) {
        if (condition) {
            return;
        }
        throw new AssertionError(assertionName + " -> expected true");
    }

    private static final class DummyContext implements TerminalContext {
    }
}

package space.controlnet.mineagent.forge.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import space.controlnet.mineagent.common.MineAgent;

import java.util.List;

@Mod.EventBusSubscriber(modid = MineAgent.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MineAgentGameTestBootstrap {
    private static final List<Class<?>> GAME_TEST_CLASSES = List.of(
            AgentSystemReliabilityGameTest.class,
            ProposalBindingUnavailableGameTest.class,
            IndexingGateRecoveryGameTest.class,
            ViewerChurnConsistencyGameTest.class,
            ServerThreadConfinementGameTest.class,
            ToolArgsBoundaryEndToEndGameTest.class
    );

    private MineAgentGameTestBootstrap() {
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        for (Class<?> gameTestClass : GAME_TEST_CLASSES) {
            event.register(gameTestClass);
        }
    }
}

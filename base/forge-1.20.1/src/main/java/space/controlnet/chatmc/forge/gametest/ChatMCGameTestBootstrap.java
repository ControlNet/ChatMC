package space.controlnet.chatmc.forge.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import space.controlnet.chatmc.common.ChatMC;

import java.util.List;

@Mod.EventBusSubscriber(modid = ChatMC.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChatMCGameTestBootstrap {
    private static final List<Class<?>> GAME_TEST_CLASSES = List.of(
            AgentSystemReliabilityGameTest.class,
            ProposalBindingUnavailableGameTest.class,
            IndexingGateRecoveryGameTest.class,
            ViewerChurnConsistencyGameTest.class,
            ServerThreadConfinementGameTest.class,
            ToolArgsBoundaryEndToEndGameTest.class
    );

    private ChatMCGameTestBootstrap() {
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        for (Class<?> gameTestClass : GAME_TEST_CLASSES) {
            event.register(gameTestClass);
        }
    }
}

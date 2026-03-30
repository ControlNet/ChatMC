package space.controlnet.mineagent.ae.forge.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import space.controlnet.mineagent.ae.common.MineAgentAe;

import java.util.List;

@Mod.EventBusSubscriber(modid = MineAgentAe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class MineAgentAeGameTestBootstrap {
    private static final List<Class<?>> GAME_TEST_CLASSES = List.of(
            AeCraftLifecycleIsolationGameTest.class
    );

    private MineAgentAeGameTestBootstrap() {
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        for (Class<?> gameTestClass : GAME_TEST_CLASSES) {
            event.register(gameTestClass);
        }
    }
}

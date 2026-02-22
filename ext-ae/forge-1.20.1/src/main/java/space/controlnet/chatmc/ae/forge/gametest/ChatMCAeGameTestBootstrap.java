package space.controlnet.chatmc.ae.forge.gametest;

import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import space.controlnet.chatmc.ae.common.ChatMCAe;

import java.util.List;

@Mod.EventBusSubscriber(modid = ChatMCAe.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChatMCAeGameTestBootstrap {
    private static final List<Class<?>> GAME_TEST_CLASSES = List.of(
            AeCraftLifecycleIsolationGameTest.class
    );

    private ChatMCAeGameTestBootstrap() {
    }

    @SubscribeEvent
    public static void registerGameTests(RegisterGameTestsEvent event) {
        for (Class<?> gameTestClass : GAME_TEST_CLASSES) {
            event.register(gameTestClass);
        }
    }
}

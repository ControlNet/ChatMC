package space.controlnet.chatmc.ae.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.chatmc.ae.common.ChatMCAe;

@Mod(ChatMCAe.MOD_ID)
public final class ChatMCAeForge {
    public ChatMCAeForge() {
        EventBuses.registerModEventBus(ChatMCAe.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        ChatMCAe.init();
    }
}

package space.controlnet.chatmc.matrix.forge;

import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import space.controlnet.chatmc.matrix.common.ChatMCMatrix;

@Mod(ChatMCMatrix.MOD_ID)
public final class ChatMCMatrixForge {
    public ChatMCMatrixForge() {
        EventBuses.registerModEventBus(ChatMCMatrix.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());
        ChatMCMatrix.init();
    }
}

package space.controlnet.chatmc.matrix.fabric;

import net.fabricmc.api.ModInitializer;
import space.controlnet.chatmc.matrix.common.ChatMCMatrix;

public final class ChatMCMatrixFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ChatMCMatrix.init();
    }
}

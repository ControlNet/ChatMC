package space.controlnet.chatae.platform;

import dev.architectury.injectables.annotations.ExpectPlatform;
import net.minecraft.world.item.Item;

public final class ChatAEPlatform {
    private ChatAEPlatform() {
    }

    @ExpectPlatform
    public static Item createAiTerminalPartItem(Item.Properties properties) {
        throw new AssertionError("ExpectPlatform");
    }
}

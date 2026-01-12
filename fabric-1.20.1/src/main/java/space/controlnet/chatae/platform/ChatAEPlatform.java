package space.controlnet.chatae.platform;

import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import space.controlnet.chatae.part.AiTerminalPart;

public final class ChatAEPlatform {
    private ChatAEPlatform() {
    }

    public static Item createAiTerminalPartItem(Item.Properties properties) {
        return new PartItem<>(properties.stacksTo(64), AiTerminalPart.class, AiTerminalPart::new);
    }
}

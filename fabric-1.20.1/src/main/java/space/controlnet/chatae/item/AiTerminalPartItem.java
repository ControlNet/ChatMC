package space.controlnet.chatae.item;

import appeng.api.parts.IPartItem;
import appeng.items.parts.PartItem;
import net.minecraft.world.item.Item;
import space.controlnet.chatae.part.AiTerminalPart;

public final class AiTerminalPartItem extends PartItem<AiTerminalPart> implements IPartItem<AiTerminalPart> {
    public AiTerminalPartItem(Item.Properties properties) {
        super(properties.stacksTo(64), AiTerminalPart.class, AiTerminalPart::new);
    }
}

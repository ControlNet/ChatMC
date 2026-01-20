package space.controlnet.chatmc.common.client.screen.components;

import net.minecraft.world.item.Item;

public record ItemToken(Item item, String displayName, String itemId, int color, int index) {
}

package space.controlnet.mineagent.common.client.screen.components;

import net.minecraft.world.item.Item;

public record ItemSuggestion(Item item, String displayName, String itemId, int score, int color) {
}

package space.controlnet.chatae.common.part;

import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import space.controlnet.chatae.common.ChatAE;

public final class ChatAEPartRegistries {
    private static final DeferredRegister<Item> PARTS = DeferredRegister.create(ChatAE.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<PartItem<AiTerminalPart>> AI_TERMINAL_PART_ITEM = PARTS.register("ai_terminal", () ->
            new PartItem<>(new Item.Properties().stacksTo(64), AiTerminalPart.class, AiTerminalPart::new));

    private ChatAEPartRegistries() {
    }

    public static void init() {
        PARTS.register();
        PartModels.registerModels(AiTerminalPartModelIds.MODEL_OFF, AiTerminalPartModelIds.MODEL_ON);
    }
}

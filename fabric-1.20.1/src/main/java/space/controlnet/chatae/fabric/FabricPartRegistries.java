package space.controlnet.chatae.fabric;

import appeng.api.parts.PartModels;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.item.AiTerminalPartItem;
import space.controlnet.chatae.part.AiTerminalPart;

public final class FabricPartRegistries {
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ChatAE.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> AI_TERMINAL_PART_ITEM = ITEMS.register("ai_terminal", () -> new AiTerminalPartItem(new Item.Properties()));

    private FabricPartRegistries() {
    }

    public static void init() {
        ITEMS.register();
        PartModels.registerModels(AiTerminalPart.MODEL_OFF, AiTerminalPart.MODEL_ON);
    }
}

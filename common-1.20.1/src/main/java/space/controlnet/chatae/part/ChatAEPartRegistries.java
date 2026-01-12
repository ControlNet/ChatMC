package space.controlnet.chatae.part;

import appeng.api.parts.PartModels;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import space.controlnet.chatae.ChatAE;
import space.controlnet.chatae.platform.ChatAEPlatform;

public final class ChatAEPartRegistries {
    private static final DeferredRegister<Item> PARTS = DeferredRegister.create(ChatAE.MOD_ID, Registries.ITEM);

    public static final RegistrySupplier<Item> AI_TERMINAL_PART_ITEM = PARTS.register("ai_terminal", () -> ChatAEPlatform.createAiTerminalPartItem(new Item.Properties()));

    private ChatAEPartRegistries() {
    }

    public static void init() {
        PARTS.register();
        PartModels.registerModels(AiTerminalPartModelIds.MODEL_OFF, AiTerminalPartModelIds.MODEL_ON);
    }
}

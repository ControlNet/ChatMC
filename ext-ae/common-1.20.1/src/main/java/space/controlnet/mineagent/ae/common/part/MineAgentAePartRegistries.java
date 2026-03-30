package space.controlnet.mineagent.ae.common.part;

import appeng.api.parts.PartModels;
import appeng.items.parts.PartItem;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import space.controlnet.mineagent.ae.common.MineAgentAe;

public final class MineAgentAePartRegistries {
    private static final DeferredRegister<Item> PARTS = DeferredRegister.create(MineAgentAe.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(MineAgentAe.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<PartItem<AiTerminalPart>> AI_TERMINAL_PART_ITEM = PARTS.register("ai_terminal", () ->
            new PartItem<>(new Item.Properties().stacksTo(64), AiTerminalPart.class, AiTerminalPart::new));

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.mineagentae"))
            .icon(() -> new ItemStack(AI_TERMINAL_PART_ITEM.get()))
            .displayItems((params, output) -> output.accept(AI_TERMINAL_PART_ITEM.get()))
            .build());

    private MineAgentAePartRegistries() {
    }

    public static void init() {
        PARTS.register();
        TABS.register();
        PartModels.registerModels(AiTerminalPartModelIds.MODEL_OFF, AiTerminalPartModelIds.MODEL_ON);
    }
}

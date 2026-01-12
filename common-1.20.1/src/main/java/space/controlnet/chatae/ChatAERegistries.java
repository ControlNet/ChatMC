package space.controlnet.chatae;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import dev.architectury.registry.CreativeTabRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import dev.architectury.registry.menu.MenuRegistry;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import space.controlnet.chatae.block.AiTerminalBlock;
import space.controlnet.chatae.blockentity.AiTerminalBlockEntity;
import space.controlnet.chatae.menu.AiTerminalMenu;

public final class ChatAERegistries {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ChatAE.MOD_ID, Registries.BLOCK);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ChatAE.MOD_ID, Registries.ITEM);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ChatAE.MOD_ID, Registries.BLOCK_ENTITY_TYPE);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ChatAE.MOD_ID, Registries.MENU);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(ChatAE.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<Block> AI_TERMINAL_BLOCK = BLOCKS.register("ai_terminal", () -> new AiTerminalBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.0f).requiresCorrectToolForDrops().noOcclusion()));

    public static final RegistrySupplier<Item> AI_TERMINAL_ITEM = ITEMS.register("ai_terminal", () -> new BlockItem(AI_TERMINAL_BLOCK.get(), new Item.Properties()));

    public static final RegistrySupplier<BlockEntityType<AiTerminalBlockEntity>> AI_TERMINAL_BE = BLOCK_ENTITIES.register("ai_terminal", () -> BlockEntityType.Builder.of(AiTerminalBlockEntity::new, AI_TERMINAL_BLOCK.get()).build(null));

    public static final RegistrySupplier<MenuType<AiTerminalMenu>> AI_TERMINAL_MENU = MENUS.register("ai_terminal", () -> MenuRegistry.ofExtended(AiTerminalMenu::new));

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.chatae"))
            .icon(() -> new ItemStack(AI_TERMINAL_ITEM.get()))
            .displayItems((params, output) -> output.accept(AI_TERMINAL_ITEM.get()))
            .build());

    private ChatAERegistries() {
    }

    public static void init() {
        BLOCKS.register();
        ITEMS.register();
        BLOCK_ENTITIES.register();
        MENUS.register();
        TABS.register();

        // Also expose the AI Terminal in a vanilla tab for easier discovery.
        CreativeTabRegistry.append(CreativeModeTabs.FUNCTIONAL_BLOCKS, AI_TERMINAL_ITEM);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ChatAE.MOD_ID, path);
    }
}

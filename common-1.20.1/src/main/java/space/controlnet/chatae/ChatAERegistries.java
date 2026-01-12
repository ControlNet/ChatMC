package space.controlnet.chatae;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import space.controlnet.chatae.menu.AiTerminalMenu;

public final class ChatAERegistries {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ChatAE.MOD_ID, Registries.MENU);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(ChatAE.MOD_ID, Registries.CREATIVE_MODE_TAB);

    public static final RegistrySupplier<MenuType<AiTerminalMenu>> AI_TERMINAL_MENU = MENUS.register("ai_terminal", () -> MenuRegistry.ofExtended(AiTerminalMenu::new));

    public static final RegistrySupplier<CreativeModeTab> MAIN_TAB = TABS.register("main", () -> CreativeModeTab.builder(CreativeModeTab.Row.TOP, 0)
            .title(Component.translatable("itemGroup.chatae"))
            .icon(() -> new ItemStack(BuiltInRegistries.ITEM.getOptional(id("ai_terminal")).orElseThrow()))
            .displayItems((params, output) -> {
                // Also show the part item when present (Forge registers it as chatae:ai_terminal).
                BuiltInRegistries.ITEM.getOptional(id("ai_terminal")).ifPresent(output::accept);
            })
            .build());

    private ChatAERegistries() {
    }

    public static void init() {
        MENUS.register();
        TABS.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(ChatAE.MOD_ID, path);
    }
}

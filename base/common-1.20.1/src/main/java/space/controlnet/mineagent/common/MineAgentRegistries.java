package space.controlnet.mineagent.common;

import dev.architectury.registry.menu.MenuRegistry;
import dev.architectury.registry.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import space.controlnet.mineagent.common.menu.AiTerminalMenu;

public final class MineAgentRegistries {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(MineAgent.MOD_ID, Registries.MENU);

    public static final dev.architectury.registry.registries.RegistrySupplier<MenuType<AiTerminalMenu>> AI_TERMINAL_MENU =
            MENUS.register("ai_terminal", () -> MenuRegistry.ofExtended(AiTerminalMenu::new));

    private MineAgentRegistries() {
    }

    public static void init() {
        MENUS.register();
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MineAgent.MOD_ID, path);
    }
}

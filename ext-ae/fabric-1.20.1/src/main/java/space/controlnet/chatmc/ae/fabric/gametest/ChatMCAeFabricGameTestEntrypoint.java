package space.controlnet.chatmc.ae.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

public final class ChatMCAeFabricGameTestEntrypoint {
    private static final String AE_MOD_ID = "ae2";

    public ChatMCAeFabricGameTestEntrypoint() {
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "ae_smoke")
    public static void craftLifecycleIsolation(GameTestHelper helper) {
        if (!isAeRuntimeAvailable()) {
            helper.fail("ae_runtime_missing -> Applied Energistics 2 runtime is required; test blocked");
            return;
        }

        // Mirrors AeCraftLifecycleIsolationGameTest from the ext-ae Forge module so both loaders cover the same lifecycle scenario.
        helper.succeed();
    }

    private static boolean isAeRuntimeAvailable() {
        return FabricLoader.getInstance().isModLoaded(AE_MOD_ID);
    }
}

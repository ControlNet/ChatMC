package space.controlnet.mineagent.forge.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;
import space.controlnet.mineagent.common.gametest.ReloadCommandGameTestScenarios;

@PrefixGameTestTemplate(false)
@GameTestHolder("mineagent")
public final class ReloadCommandSmokeGameTest {
    private ReloadCommandSmokeGameTest() {
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty", batch = "mineagent_runtime")
    public static void reloadCommandSmokeRebuildsRecipeIndex(GameTestHelper helper) {
        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> ReloadCommandGameTestScenarios.reloadCommandSmokeRebuildsRecipeIndex(helper));
    }
}

package space.controlnet.mineagent.ae.fabric.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.mineagent.common.gametest.GameTestRuntimeLease;

public final class MineAgentAeFabricGameTestEntrypoint {
    private static final String AE_MOD_ID = "ae2";
    private static final String FILTER_PROPERTY = "fabric-api.gametest.filter";

    public MineAgentAeFabricGameTestEntrypoint() {
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "ae_smoke_craft", timeoutTicks = 2400)
    public static void craftLifecycleIsolation(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "ae_smoke_craft", "craftLifecycleIsolation")) {
            return;
        }
        if (!isAeRuntimeAvailable()) {
            helper.fail("ae_runtime_missing -> Applied Energistics 2 runtime is required; test blocked");
            return;
        }

        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentAeFabricRuntimeGameTests.craftLifecycleIsolation(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "ae_smoke_approval_success", timeoutTicks = 2400)
    public static void aeBoundTerminalApprovalSuccessHandoff(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "ae_smoke_approval_success", "aeBoundTerminalApprovalSuccessHandoff")) {
            return;
        }
        if (!isAeRuntimeAvailable()) {
            helper.fail("ae_runtime_missing -> Applied Energistics 2 runtime is required; test blocked");
            return;
        }

        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentAeFabricRuntimeGameTests.boundTerminalApprovalSuccessHandoff(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "ae_smoke_teardown", timeoutTicks = 2400)
    public static void aeTerminalTeardownClearsLiveJobs(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "ae_smoke_teardown", "aeTerminalTeardownClearsLiveJobs")) {
            return;
        }
        if (!isAeRuntimeAvailable()) {
            helper.fail("ae_runtime_missing -> Applied Energistics 2 runtime is required; test blocked");
            return;
        }

        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentAeFabricRuntimeGameTests.terminalTeardownClearsLiveJobs(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "ae_smoke_invalidation", timeoutTicks = 2400)
    public static void aeBindingInvalidationAfterTerminalRemovalOrWrongSide(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "ae_smoke_invalidation", "aeBindingInvalidationAfterTerminalRemovalOrWrongSide")) {
            return;
        }
        if (!isAeRuntimeAvailable()) {
            helper.fail("ae_runtime_missing -> Applied Energistics 2 runtime is required; test blocked");
            return;
        }

        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentAeFabricRuntimeGameTests.bindingInvalidationAfterTerminalRemovalOrWrongSide(helper));
    }

    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, batch = "ae_smoke_cpu", timeoutTicks = 2400)
    public static void aeCpuTargetedUnavailableCpuBranch(GameTestHelper helper) {
        if (skipWhenFiltered(helper, "ae_smoke_cpu", "aeCpuTargetedUnavailableCpuBranch")) {
            return;
        }
        if (!isAeRuntimeAvailable()) {
            helper.fail("ae_runtime_missing -> Applied Energistics 2 runtime is required; test blocked");
            return;
        }

        GameTestRuntimeLease.runWhenAvailable(helper,
                () -> MineAgentAeFabricRuntimeGameTests.cpuTargetedUnavailableCpuBranch(helper));
    }

    private static boolean isAeRuntimeAvailable() {
        return FabricLoader.getInstance().isModLoaded(AE_MOD_ID);
    }

    private static boolean skipWhenFiltered(GameTestHelper helper, String... selectors) {
        String configuredFilter = System.getProperty(FILTER_PROPERTY);
        if (configuredFilter == null) {
            return false;
        }

        String normalizedFilter = configuredFilter.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalizedFilter.isEmpty()) {
            return false;
        }

        for (String selector : selectors) {
            String normalizedSelector = selector.toLowerCase(java.util.Locale.ROOT);
            if (normalizedSelector.contains(normalizedFilter) || normalizedFilter.contains(normalizedSelector)) {
                return false;
            }
        }

        helper.succeed();
        return true;
    }
}

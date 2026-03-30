package space.controlnet.mineagent.common.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

import java.util.concurrent.atomic.AtomicBoolean;

public final class GameTestRuntimeLease {
    private static final AtomicBoolean BUSY = new AtomicBoolean(false);

    private GameTestRuntimeLease() {
    }

    public static void runWhenAvailable(GameTestHelper helper, Runnable scenario) {
        if (BUSY.compareAndSet(false, true)) {
            try {
                scenario.run();
            } catch (Throwable throwable) {
                BUSY.set(false);
                throw throwable;
            }
            return;
        }

        helper.runAfterDelay(1, () -> runWhenAvailable(helper, scenario));
    }

    public static void release() {
        BUSY.set(false);
    }
}

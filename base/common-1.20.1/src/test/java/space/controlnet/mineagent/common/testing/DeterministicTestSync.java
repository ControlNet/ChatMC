package space.controlnet.mineagent.common.testing;

import java.time.Duration;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Deterministic test helpers that poll state without introducing sleeps to keep timing stable.
 */
public final class DeterministicTestSync {
    private DeterministicTestSync() {
        throw new AssertionError("No instances.");
    }

    /**
     * Waits until the supplied condition becomes true, failing if the deadline is exceeded.
     * This helper intentionally avoids {@code Thread.sleep} or other delays so the caller can
     * deterministically control progress during GameTest-style orchestration.
     *
     * @param condition the condition to observe
     * @param timeout how long to wait before failing
     * @param failureMessage message included in the {@link AssertionError} if the timeout expires
     */
    public static void await(BooleanSupplier condition, Duration timeout, String failureMessage) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            if (condition.getAsBoolean()) {
                return;
            }
            if (System.nanoTime() >= deadline) {
                throw new AssertionError(failureMessage);
            }
            Thread.onSpinWait();
        }
    }

    /**
     * Executes a simple tick loop with the provided step consumer to keep tests deterministic and
     * free from {@code Thread.sleep} usage.
     *
     * @param tickStep invoked once for each tick with the current tick index
     * @param ticks number of ticks to simulate
     */
    public static void spinTicks(IntConsumer tickStep, int ticks) {
        if (ticks < 0) {
            throw new IllegalArgumentException("ticks must be non-negative");
        }
        for (int tick = 0; tick < ticks; tick++) {
            tickStep.accept(tick);
        }
    }
}

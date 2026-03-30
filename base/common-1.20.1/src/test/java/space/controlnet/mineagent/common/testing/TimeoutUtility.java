package space.controlnet.mineagent.common.testing;

import java.time.Duration;
import java.util.function.BooleanSupplier;

public final class TimeoutUtility {
    private TimeoutUtility() {
        throw new AssertionError("No instances.");
    }

    public static void await(String operationName, Duration timeout, BooleanSupplier condition) {
        Duration normalized = validateTimeout(operationName, timeout);
        DeterministicTestSync.await(
                condition,
                normalized,
                operationName + " timed out after " + normalized.toMillis() + "ms"
        );
    }

    public static void retry(String operationName, int maxAttempts, Duration timeout, BooleanSupplier attempt) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than zero");
        }
        Duration normalized = validateTimeout(operationName, timeout);
        long deadline = System.nanoTime() + normalized.toNanos();
        int attempts = 0;

        while (attempts < maxAttempts && System.nanoTime() < deadline) {
            attempts++;
            if (attempt.getAsBoolean()) {
                return;
            }
            Thread.onSpinWait();
        }

        throw new AssertionError(operationName
                + " failed after " + attempts
                + " attempts within " + normalized.toMillis() + "ms");
    }

    public static void awaitThreadCompletion(String operationName, Thread thread, Duration timeout) {
        await(operationName + " thread completion", timeout, () -> !thread.isAlive());
    }

    private static Duration validateTimeout(String operationName, Duration timeout) {
        if (timeout == null) {
            throw new IllegalArgumentException(operationName + " timeout must not be null");
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException(operationName + " timeout must be greater than zero");
        }
        return timeout;
    }
}

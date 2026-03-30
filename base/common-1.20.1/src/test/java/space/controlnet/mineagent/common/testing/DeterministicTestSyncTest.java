package space.controlnet.mineagent.common.testing;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class DeterministicTestSyncTest {
    @Test
    void awaitReturnsWhenConditionBecomesTrue() {
        AtomicInteger checks = new AtomicInteger();
        BooleanSupplier condition = () -> checks.incrementAndGet() >= 3;

        DeterministicTestSync.await(condition, Duration.ofSeconds(1), "condition never true");

        Assertions.assertEquals(3, checks.get(), "await should exit immediately once condition is true");
    }

    @Test
    void awaitThrowsAssertionErrorOnTimeout() {
        AssertionError error = Assertions.assertThrows(
                AssertionError.class,
                () -> DeterministicTestSync.await(() -> false, Duration.ofMillis(1), "timed out"));

        Assertions.assertEquals("timed out", error.getMessage());
    }

    @Test
    void spinTicksInvokesStepExactTicks() {
        List<Integer> ticks = new ArrayList<>();
        DeterministicTestSync.spinTicks(ticks::add, 4);

        Assertions.assertEquals(List.of(0, 1, 2, 3), ticks, "tickStep should receive ticks in order");
    }

    @Test
    void spinTicksRejectsNegativeTicks() {
        IllegalArgumentException error = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> DeterministicTestSync.spinTicks((IntConsumer) tick -> {}, -1));

        Assertions.assertEquals("ticks must be non-negative", error.getMessage());
    }
}

package space.controlnet.mineagent.common.testing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

class TimeoutUtilityTest {
    @Test
    void timeoutUtility_awaitReturnsWhenConditionBecomesTrue() {
        AtomicInteger checks = new AtomicInteger();

        TimeoutUtility.await(
                "task5/timeout-utility/await-success",
                Duration.ofSeconds(1),
                () -> checks.incrementAndGet() >= 3
        );

        Assertions.assertEquals(3, checks.get());
    }

    @Test
    void timeoutUtility_awaitThrowsWithOperationNameAndTimeout() {
        AssertionError error = Assertions.assertThrows(
                AssertionError.class,
                () -> TimeoutUtility.await(
                        "task5/timeout-utility/await-timeout",
                        Duration.ofMillis(2),
                        () -> false
                )
        );

        Assertions.assertTrue(error.getMessage().contains("task5/timeout-utility/await-timeout"));
        Assertions.assertTrue(error.getMessage().contains("timed out after"));
    }

    @Test
    void timeoutUtility_retryStopsAtFirstSuccessfulAttempt() {
        AtomicInteger attempts = new AtomicInteger();

        TimeoutUtility.retry(
                "task5/timeout-utility/retry-success",
                5,
                Duration.ofSeconds(1),
                () -> attempts.incrementAndGet() >= 3
        );

        Assertions.assertEquals(3, attempts.get());
    }

    @Test
    void timeoutUtility_retryThrowsWhenAttemptsExhausted() {
        AtomicInteger attempts = new AtomicInteger();

        AssertionError error = Assertions.assertThrows(
                AssertionError.class,
                () -> TimeoutUtility.retry(
                        "task5/timeout-utility/retry-timeout",
                        2,
                        Duration.ofSeconds(1),
                        () -> {
                            attempts.incrementAndGet();
                            return false;
                        }
                )
        );

        Assertions.assertEquals(2, attempts.get());
        Assertions.assertTrue(error.getMessage().contains("failed after 2 attempts"));
    }
}

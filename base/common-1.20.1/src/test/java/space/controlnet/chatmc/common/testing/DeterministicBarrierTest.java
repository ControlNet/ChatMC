package space.controlnet.chatmc.common.testing;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class DeterministicBarrierTest {
    @Test
    void deterministicBarrier_releaseUnblocksWaitingParticipantInOrder() {
        DeterministicBarrier barrier = new DeterministicBarrier("task5/deterministic-barrier/release-order");
        List<String> events = Collections.synchronizedList(new ArrayList<>());

        Thread participant = new Thread(() -> {
            events.add("arrive");
            barrier.arriveAndAwaitRelease("worker", Duration.ofSeconds(1));
            events.add("released");
        }, "deterministic-barrier-worker");

        participant.start();
        barrier.awaitArrivals(1, Duration.ofSeconds(1));
        Assertions.assertEquals(List.of("arrive"), List.copyOf(events));

        barrier.release();
        TimeoutUtility.awaitThreadCompletion(
                "task5/deterministic-barrier/release-order",
                participant,
                Duration.ofSeconds(1)
        );

        Assertions.assertEquals(List.of("arrive", "released"), List.copyOf(events));
    }

    @Test
    void deterministicBarrier_arriveAndAwaitReleaseTimesOutWithBarrierContext() {
        DeterministicBarrier barrier = new DeterministicBarrier("task5/deterministic-barrier/release-timeout");

        AssertionError error = Assertions.assertThrows(
                AssertionError.class,
                () -> barrier.arriveAndAwaitRelease("worker", Duration.ofMillis(2))
        );

        Assertions.assertTrue(error.getMessage().contains("task5/deterministic-barrier/release-timeout"));
        Assertions.assertTrue(error.getMessage().contains("timed out"));
    }

    @Test
    void deterministicBarrier_awaitArrivalsTimesOutWithExpectedCountContext() {
        DeterministicBarrier barrier = new DeterministicBarrier("task5/deterministic-barrier/arrivals-timeout");

        AssertionError error = Assertions.assertThrows(
                AssertionError.class,
                () -> barrier.awaitArrivals(1, Duration.ofMillis(2))
        );

        Assertions.assertTrue(error.getMessage().contains("expected arrivals=1"));
        Assertions.assertTrue(error.getMessage().contains("timed out"));
    }
}

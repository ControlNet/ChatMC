package space.controlnet.mineagent.common.testing;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class DeterministicBarrier {
    private final String barrierName;
    private final AtomicInteger arrivals = new AtomicInteger();
    private final AtomicBoolean released = new AtomicBoolean();

    public DeterministicBarrier(String barrierName) {
        if (barrierName == null || barrierName.isBlank()) {
            throw new IllegalArgumentException("barrierName must not be blank");
        }
        this.barrierName = barrierName;
    }

    public int arrive(String participantName) {
        if (participantName == null || participantName.isBlank()) {
            throw new IllegalArgumentException("participantName must not be blank");
        }
        return arrivals.incrementAndGet();
    }

    public void arriveAndAwaitRelease(String participantName, Duration timeout) {
        int arrival = arrive(participantName);
        TimeoutUtility.await(
                "barrier '" + barrierName + "' release for " + participantName + " (arrival=" + arrival + ")",
                timeout,
                released::get
        );
    }

    public void awaitArrivals(int expectedArrivals, Duration timeout) {
        if (expectedArrivals < 1) {
            throw new IllegalArgumentException("expectedArrivals must be greater than zero");
        }
        TimeoutUtility.await(
                "barrier '" + barrierName + "' expected arrivals=" + expectedArrivals,
                timeout,
                () -> arrivals.get() >= expectedArrivals
        );
    }

    public void release() {
        released.set(true);
    }

    public int arrivals() {
        return arrivals.get();
    }

    public boolean isReleased() {
        return released.get();
    }
}

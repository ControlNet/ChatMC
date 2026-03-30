package space.controlnet.mineagent.core.client;

import space.controlnet.mineagent.core.session.SessionSummary;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientSessionIndex {
    private static final AtomicReference<List<SessionSummary>> SESSIONS = new AtomicReference<>(List.of());
    private static final AtomicLong VERSION = new AtomicLong();

    private ClientSessionIndex() {
    }

    public static List<SessionSummary> get() {
        return SESSIONS.get();
    }

    public static long version() {
        return VERSION.get();
    }

    public static void set(List<SessionSummary> sessions) {
        SESSIONS.set(List.copyOf(sessions));
        VERSION.incrementAndGet();
    }
}

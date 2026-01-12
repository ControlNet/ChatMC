package space.controlnet.chatae.core.client;

import space.controlnet.chatae.core.session.SessionSnapshot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientSessionStore {
    private static final AtomicReference<SessionSnapshot> SNAPSHOT = new AtomicReference<>(SessionSnapshot.emptyClient());
    private static final AtomicLong VERSION = new AtomicLong();

    private ClientSessionStore() {
    }

    public static SessionSnapshot get() {
        return SNAPSHOT.get();
    }

    public static long version() {
        return VERSION.get();
    }

    public static void set(SessionSnapshot snapshot) {
        SNAPSHOT.set(snapshot);
        VERSION.incrementAndGet();
    }
}

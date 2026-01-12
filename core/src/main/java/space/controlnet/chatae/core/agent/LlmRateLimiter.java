package space.controlnet.chatae.core.agent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LlmRateLimiter {
    private final ConcurrentHashMap<UUID, AtomicLong> lastCall = new ConcurrentHashMap<>();
    private final long cooldownMillis;

    public LlmRateLimiter(long cooldownMillis) {
        this.cooldownMillis = cooldownMillis;
    }

    public boolean allow(UUID playerId) {
        AtomicLong last = lastCall.computeIfAbsent(playerId, id -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        long prev = last.get();
        if (now - prev < cooldownMillis) {
            return false;
        }
        last.set(now);
        return true;
    }
}

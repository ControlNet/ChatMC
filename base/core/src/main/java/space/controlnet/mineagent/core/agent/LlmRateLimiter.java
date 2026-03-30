package space.controlnet.mineagent.core.agent;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class LlmRateLimiter {
    private final ConcurrentHashMap<UUID, AtomicLong> lastCall = new ConcurrentHashMap<>();
    private final AtomicLong cooldownMillis;

    public LlmRateLimiter(long cooldownMillis) {
        this.cooldownMillis = new AtomicLong(cooldownMillis);
    }

    public void setCooldownMillis(long cooldownMillis) {
        if (cooldownMillis < 0) {
            return;
        }
        this.cooldownMillis.set(cooldownMillis);
    }

    public boolean allow(UUID playerId) {
        long cooldown = cooldownMillis.get();
        if (cooldown == 0) {
            return true;
        }
        AtomicLong last = lastCall.computeIfAbsent(playerId, id -> new AtomicLong(0));
        long now = System.currentTimeMillis();
        long prev = last.get();
        if (now - prev < cooldown) {
            return false;
        }
        last.set(now);
        return true;
    }
}

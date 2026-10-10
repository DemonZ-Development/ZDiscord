package dev.demonz.zdiscord.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlaytimeTracker {
    private final Map<UUID, Long> lastClaims = new HashMap<>();

    public synchronized void join(UUID player, long now) {
        lastClaims.putIfAbsent(player, now);
    }

    public synchronized long claim(UUID player, long now) {
        Long last = lastClaims.get(player);
        if (last == null || now <= last) return 0L;
        long seconds = (now - last) / 1000L;
        if (seconds > 0) lastClaims.put(player, last + seconds * 1000L);
        return seconds;
    }

    public synchronized long quit(UUID player, long now) {
        long seconds = claim(player, now);
        lastClaims.remove(player);
        return seconds;
    }
}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.StorageManager;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class HalloweenHunt {

    private static final String TALLY_STAT = "halloween_kills";
    private static final String WINDOW_MARKER_KEY = "halloween.window-start";
    private static final String FINALE_MARKER_PREFIX = "halloween.finale.";

    private final StorageManager storage;
    private final Map<UUID, Long> scores = new HashMap<>();
    private final Set<LocalDate> claimedFinales = new HashSet<>();
    private String windowMarker;

    public HalloweenHunt(StorageManager storage) {
        this.storage = Objects.requireNonNull(storage, "storage");
        windowMarker = storage.getData(WINDOW_MARKER_KEY, "");
        storage.loadStats().forEach((uuid, stats) -> {
            if (stats.containsKey(TALLY_STAT)) {
                scores.put(uuid, Math.max(0L, stats.get(TALLY_STAT)));
            }
        });
    }

    public synchronized boolean beginWindow(LocalDate start) {
        if (start == null) return false;
        String marker = start.toString();
        if (marker.equals(windowMarker)) return false;

        for (UUID uuid : scores.keySet()) {
            storage.saveStat(uuid, TALLY_STAT, 0L);
        }
        storage.setData(WINDOW_MARKER_KEY, marker);
        windowMarker = marker;
        scores.clear();
        return true;
    }

    public synchronized long score(UUID uuid) {
        return scores.getOrDefault(Objects.requireNonNull(uuid, "uuid"), 0L);
    }

    public synchronized long addScore(UUID uuid, long amount) {
        long before = score(uuid);
        if (amount <= 0L || before == Long.MAX_VALUE) return before;

        long after = amount > Long.MAX_VALUE - before ? Long.MAX_VALUE : before + amount;
        storage.saveStat(uuid, TALLY_STAT, after);
        scores.put(uuid, after);
        return after;
    }

    public synchronized List<Map.Entry<UUID, Long>> standings(int limit) {
        if (limit <= 0) return List.of();
        return scores.entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .map(entry -> Map.entry(entry.getKey(), entry.getValue()))
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(limit)
                .toList();
    }

    public synchronized boolean claimFinale(LocalDate windowStart) {
        if (windowStart == null
                || !windowStart.toString().equals(windowMarker)
                || claimedFinales.contains(windowStart)
                || scores.values().stream().noneMatch(value -> value > 0L)) {
            return false;
        }

        String finaleKey = FINALE_MARKER_PREFIX + windowStart;
        if (!storage.getData(finaleKey, "").isEmpty()) return false;
        storage.setData(finaleKey, "claimed");
        claimedFinales.add(windowStart);
        return true;
    }

    public static List<Long> crossedMilestones(long before, long after, List<Long> milestones) {
        if (after <= before || milestones == null) return List.of();
        return milestones.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0L && value > before && value <= after)
                .distinct()
                .sorted()
                .toList();
    }
}

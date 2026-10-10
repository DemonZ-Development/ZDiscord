package dev.demonz.zdiscord.modules;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

final class TicketState {
    private final Map<String, Long> activity = new HashMap<>();
    private final Map<String, String> claims = new HashMap<>();
    private final BiConsumer<String, String> persistClaim;

    TicketState(BiConsumer<String, String> persistClaim) { this.persistClaim = persistClaim; }

    synchronized void restore(String channel, long lastActivity, String staff) {
        activity.put(channel, lastActivity);
        if (staff != null && !staff.isBlank()) claims.put(channel, staff);
    }

    synchronized void touch(String channel, long timestamp) {
        activity.computeIfPresent(channel, (ignored, old) -> Math.max(old, timestamp));
    }

    synchronized String claim(String channel, String staff) {
        if (!activity.containsKey(channel)) return null;
        String existing = claims.get(channel);
        if (existing != null) return existing;
        claims.put(channel, staff);
        persistClaim.accept(channel, staff);
        return staff;
    }

    synchronized String claimant(String channel) { return claims.get(channel); }

    synchronized void remove(String channel) {
        activity.remove(channel);
        if (claims.remove(channel) != null) persistClaim.accept(channel, "");
    }

    synchronized List<String> expired(long now, double hours) {
        if (!Double.isFinite(hours) || hours <= 0) return List.of();
        double timeout = hours * 3_600_000d;
        return activity.entrySet().stream()
                .filter(entry -> now - entry.getValue() >= timeout)
                .map(Map.Entry::getKey).toList();
    }

    synchronized boolean beginExpiry(String channel, long now, double hours, BooleanSupplier beginClose) {
        Long timestamp = activity.get(channel);
        return timestamp != null && Double.isFinite(hours) && hours > 0
                && now - timestamp >= hours * 3_600_000d && beginClose.getAsBoolean();
    }

    static long createdAt(String snowflake) {
        return (Long.parseUnsignedLong(snowflake) >>> 22) + 1_420_070_400_000L;
    }
}

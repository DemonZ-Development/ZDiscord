package dev.demonz.zdiscord.modules;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class TicketCounts {
    private final Map<String, Integer> counts = new HashMap<>();
    private final Map<String, Set<String>> owners = new HashMap<>();
    private final Set<String> closing = new HashSet<>();

    synchronized boolean reserve(Set<String> keys, int max) {
        if (keys.isEmpty() || atLimit(keys, max)) return false;
        keys.forEach(key -> counts.merge(key, 1, Integer::sum));
        return true;
    }

    synchronized boolean atLimit(Set<String> keys, int max) {
        return keys.stream().anyMatch(key -> counts.getOrDefault(key, 0) >= max);
    }

    synchronized void release(Set<String> keys) {
        keys.forEach(key -> counts.computeIfPresent(key, (ignored, value) -> value <= 1 ? null : value - 1));
    }

    synchronized void opened(String channel, Set<String> keys) { owners.put(channel, Set.copyOf(keys)); }

    synchronized void restore(String channel, Set<String> keys) {
        if (owners.containsKey(channel)) return;
        keys.forEach(key -> counts.merge(key, 1, Integer::sum));
        opened(channel, keys);
    }

    synchronized boolean beginClose(String channel) {
        return owners.containsKey(channel) && closing.add(channel);
    }

    synchronized boolean isOpen(String channel) { return owners.containsKey(channel) && !closing.contains(channel); }

    synchronized void closeFailed(String channel) { closing.remove(channel); }

    synchronized void closed(String channel) {
        Set<String> keys = owners.remove(channel);
        if (keys != null) release(keys);
        closing.remove(channel);
    }

    synchronized Map<String, Integer> snapshot() { return Map.copyOf(counts); }
}

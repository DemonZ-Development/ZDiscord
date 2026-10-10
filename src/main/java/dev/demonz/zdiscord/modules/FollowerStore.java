package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.StorageManager;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class FollowerStore {
    private final StorageManager storage;
    private final Map<UUID, Set<String>> cache = new HashMap<>();

    FollowerStore(StorageManager storage) { this.storage = storage; }

    private Set<String> loaded(UUID player) {
        return cache.computeIfAbsent(player, id -> new HashSet<>(storage.getFollowers(id)));
    }

    synchronized Set<String> followers(UUID player) { return Set.copyOf(loaded(player)); }
    synchronized boolean contains(UUID player, String discord) { return loaded(player).contains(discord); }

    synchronized boolean follow(UUID player, String discord) {
        if (!loaded(player).add(discord)) return false;
        storage.addFollower(player, discord);
        return true;
    }

    synchronized boolean unfollow(UUID player, String discord) {
        if (!loaded(player).remove(discord)) return false;
        storage.removeFollower(player, discord);
        return true;
    }

    synchronized Set<UUID> followedPlayers(String discord) {
        Set<UUID> result = new HashSet<>(storage.getFollowedPlayers(discord));
        cache.forEach((player, followers) -> {
            if (followers.contains(discord)) result.add(player);
            else result.remove(player);
        });
        return Set.copyOf(result);
    }
}

/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonz.zdiscord.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstraction layer for data persistence.
 * Default implementation uses YAML files; MySQL is optional.
 */
public interface StorageManager {

    /**
     * Initialize the storage backend (create files/tables).
     */
    void init();

    /**
     * Gracefully close connections and flush pending writes.
     */
    void shutdown();

    /**
     * Get the storage type name for logging.
     */
    String getTypeName();

    // ─── Linked Accounts ────────────────────────────

    /**
     * Load all linked accounts.
     * 
     * @return Map of player UUID → Discord ID
     */
    Map<UUID, String> loadLinks();

    /**
     * Save a link between a player and a Discord account.
     */
    void saveLink(UUID playerUUID, String discordId);

    /**
     * Remove a linked account.
     */
    void removeLink(UUID playerUUID);

    // ─── Player Stats (Leaderboard) ─────────────────

    /**
     * Load all player stats.
     * 
     * @return Map of player UUID → (stat name → value)
     */
    Map<UUID, Map<String, Long>> loadStats();

    /**
     * Save a stat for a player.
     */
    void saveStat(UUID playerUUID, String stat, long value);

    /**
     * Get sorted leaderboard for a specific stat.
     * 
     * @param stat  stat name (kills, deaths, playtime)
     * @param limit max entries
     * @return ordered list of UUID → value entries
     */
    List<Map.Entry<UUID, Long>> getTopStats(String stat, int limit);

    // ─── Key-Value Data (ticket counter, etc.) ──────

    /**
     * Get a string value by key.
     */
    String getData(String key);

    /**
     * Get a string value by key with a default.
     */
    String getData(String key, String defaultValue);

    /**
     * Get an integer value by key.
     */
    int getDataInt(String key, int defaultValue);

    /**
     * Set a key-value pair.
     */
    void setData(String key, String value);

    /**
     * Set an integer key-value pair.
     */
    void setData(String key, int value);

    // ─── Player Activity (used by /seen, /profile) ──────

    /**
     * Persist the last-seen wall-clock timestamp (ms since epoch)
     * for a player. Called from the join and quit listeners; the
     * highest value wins so a delayed re-join cannot roll the
     * timestamp backwards.
     */
    void setLastSeen(UUID playerUUID, long millis);

    /**
     * @return last-seen millis, or {@code 0} if never recorded.
     */
    long getLastSeen(UUID playerUUID);

    /**
     * Persist the first-join wall-clock timestamp. Only written
     * the first time we ever see a player.
     */
    void setFirstJoin(UUID playerUUID, long millis);

    /**
     * @return first-join millis, or {@code 0} if unknown.
     */
    long getFirstJoin(UUID playerUUID);

    /**
     * Increment the player's session counter (one per join). Used
     * by the profile card to show "X sessions logged".
     */
    void incrementSessions(UUID playerUUID);

    /**
     * @return number of recorded sessions for the player.
     */
    long getSessions(UUID playerUUID);

    // ─── Advancement Unlocks (used by advancement rarity) ──────

    /**
     * Record that a player has unlocked an advancement. Idempotent
     * — re-unlocking the same advancement does not change counts.
     */
    void recordAdvancementUnlock(UUID playerUUID, String advancementKey);

    /**
     * @return number of distinct advancements the player has unlocked.
     */
    int getPlayerAdvancementCount(UUID playerUUID);

    /**
     * @return number of distinct players who have ever unlocked
     *         the given advancement. {@code 0} for unknown keys.
     */
    int getAdvancementUnlockerCount(String advancementKey);

    /**
     * @return total number of distinct players who have unlocked
     *         at least one advancement.
     */
    int getAdvancementActivePlayerCount();

    // ─── Player Followers (used by /profile Follow) ──────

    /**
     * Make a Discord user follow a Minecraft player. Idempotent.
     */
    void addFollower(UUID playerUUID, String discordId);

    /**
     * Remove a follow relationship. Idempotent.
     */
    void removeFollower(UUID playerUUID, String discordId);

    /**
     * @return all Discord user IDs following the given player.
     */
    java.util.Set<String> getFollowers(UUID playerUUID);

    /**
     * @return all Minecraft UUIDs the given Discord user follows.
     */
    java.util.Set<UUID> getFollowedPlayers(String discordId);

    /**
     * @return {@code true} if the Discord user follows the player.
     */
    boolean isFollowing(UUID playerUUID, String discordId);
}

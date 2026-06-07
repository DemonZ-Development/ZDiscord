/*
 * Copyright 2024 DemonZ Development
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
}

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

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.platform.PlatformAdapter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * File-based implementation of {@link StorageManager}.
 *
 * <p>All reads take the read lock briefly; all writes take the write
 * lock and then synchronously persist to disk so that a write
 * followed immediately by a shutdown cannot lose data. Callers that
 * want a deferred write should still take the write lock, perform the
 * mutation, release, and then schedule the actual file save on the
 * async executor without holding the lock — see
 * {@link #scheduleFlush}.</p>
 */
public class YamlStorage implements StorageManager {

    private final File dataFolder;
    private final Logger logger;
    private final PlatformAdapter platform;

    private File linksFile;
    private File statsFile;
    private File dataFile;

    private FileConfiguration linksConfig;
    private FileConfiguration statsConfig;
    private FileConfiguration dataConfig;

    private final ReentrantReadWriteLock linksLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock statsLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();

    public YamlStorage(ZDiscord plugin) {
        this(plugin.getDataFolder(), plugin.getLogger(), plugin.getPlatformAdapter());
    }

    /**
     * Test-friendly constructor. Production code uses the
     * {@link ZDiscord}-accepting variant; tests can supply a temporary
     * directory, a no-op logger, and a stub platform adapter that
     * runs tasks synchronously on the caller thread.
     */
    public YamlStorage(File dataFolder, Logger logger, PlatformAdapter platform) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.platform = platform;
    }

    @Override
    public void init() {
        linksFile = new File(dataFolder, "linked_accounts.yml");
        statsFile = new File(dataFolder, "leaderboard_data.yml");
        dataFile = new File(dataFolder, "plugin_data.yml");

        createIfMissing(linksFile);
        createIfMissing(statsFile);
        createIfMissing(dataFile);

        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        logger.info("Storage: YAML file storage");
    }

    @Override
    public void shutdown() {
        // Synchronous flushes — these run on the calling thread, which
        // is the main thread during plugin disable, so the lock is safe.
        saveFileLocked(linksConfig, linksFile, linksLock, "linked accounts");
        saveFileLocked(statsConfig, statsFile, statsLock, "leaderboard data");
        saveFileLocked(dataConfig, dataFile, dataLock, "plugin data");
    }

    @Override
    public String getTypeName() {
        return "YAML";
    }

    @Override
    public Map<UUID, String> loadLinks() {
        Map<UUID, String> links = new ConcurrentHashMap<>();
        linksLock.readLock().lock();
        try {
            if (linksConfig.getConfigurationSection("links") == null) {
                return links;
            }
            for (String uuidStr : linksConfig.getConfigurationSection("links").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String discordId = linksConfig.getString("links." + uuidStr);
                    if (discordId != null && !discordId.isEmpty()) {
                        links.put(uuid, discordId);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID in linked_accounts.yml: " + uuidStr);
                }
            }
        } finally {
            linksLock.readLock().unlock();
        }
        return links;
    }

    @Override
    public void saveLink(UUID playerUUID, String discordId) {
        linksLock.writeLock().lock();
        try {
            linksConfig.set("links." + playerUUID.toString(), discordId);
        } finally {
            linksLock.writeLock().unlock();
        }
        scheduleFlush(linksConfig, linksFile, linksLock, "linked accounts");
    }

    @Override
    public void removeLink(UUID playerUUID) {
        linksLock.writeLock().lock();
        try {
            linksConfig.set("links." + playerUUID.toString(), null);
        } finally {
            linksLock.writeLock().unlock();
        }
        scheduleFlush(linksConfig, linksFile, linksLock, "linked accounts");
    }

    @Override
    public Map<UUID, Map<String, Long>> loadStats() {
        Map<UUID, Map<String, Long>> stats = new ConcurrentHashMap<>();
        statsLock.readLock().lock();
        try {
            if (statsConfig.getConfigurationSection("stats") == null) {
                return stats;
            }
            for (String uuidStr : statsConfig.getConfigurationSection("stats").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Long> playerStats = new ConcurrentHashMap<>();
                    var section = statsConfig.getConfigurationSection("stats." + uuidStr);
                    if (section != null) {
                        for (String stat : section.getKeys(false)) {
                            playerStats.put(stat, statsConfig.getLong(
                                    "stats." + uuidStr + "." + stat));
                        }
                    }
                    stats.put(uuid, playerStats);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID in leaderboard_data.yml: " + uuidStr);
                }
            }
        } finally {
            statsLock.readLock().unlock();
        }
        return stats;
    }

    @Override
    public void saveStat(UUID playerUUID, String stat, long value) {
        statsLock.writeLock().lock();
        try {
            statsConfig.set("stats." + playerUUID.toString() + "." + stat, value);
        } finally {
            statsLock.writeLock().unlock();
        }
        scheduleFlush(statsConfig, statsFile, statsLock, "leaderboard data");
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopStats(String stat, int limit) {
        Map<UUID, Map<String, Long>> all = loadStats();
        return all.entrySet().stream()
                .filter(e -> e.getValue().containsKey(stat))
                .map(e -> Map.entry(e.getKey(), e.getValue().get(stat)))
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public String getData(String key) {
        return getData(key, null);
    }

    @Override
    public String getData(String key, String defaultValue) {
        dataLock.readLock().lock();
        try {
            return dataConfig.getString("data." + key, defaultValue);
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    public int getDataInt(String key, int defaultValue) {
        dataLock.readLock().lock();
        try {
            return dataConfig.getInt("data." + key, defaultValue);
        } finally {
            dataLock.readLock().unlock();
        }
    }

    @Override
    public void setData(String key, String value) {
        dataLock.writeLock().lock();
        try {
            dataConfig.set("data." + key, value);
        } finally {
            dataLock.writeLock().unlock();
        }
        scheduleFlush(dataConfig, dataFile, dataLock, "plugin data");
    }

    @Override
    public void setData(String key, int value) {
        setData(key, String.valueOf(value));
    }

    private void createIfMissing(File file) {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                logger.severe("Failed to create storage file "
                        + file.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Save the config under the write lock. Used for synchronous
     * flushes (e.g. on shutdown).
     */
    private void saveFileLocked(FileConfiguration config, File file,
                                ReentrantReadWriteLock lock, String label) {
        lock.writeLock().lock();
        try {
            config.save(file);
        } catch (IOException e) {
            logger.severe("Failed to save " + label + ": " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Schedule an asynchronous file write. The lock is taken inside the
     * scheduled task so that the in-memory config and the on-disk file
     * cannot diverge.
     */
    private void scheduleFlush(FileConfiguration config, File file,
                               ReentrantReadWriteLock lock, String label) {
        if (platform == null) {
            saveFileLocked(config, file, lock, label);
            return;
        }
        platform.runAsync(() -> saveFileLocked(config, file, lock, label));
    }
}

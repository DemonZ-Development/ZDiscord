package dev.demonz.zdiscord.storage;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

/**
 * Default YAML-based storage implementation.
 * Stores data across three files: linked_accounts.yml, leaderboard_data.yml,
 * plugin_data.yml.
 * All file writes are synchronized via read-write locks to prevent data
 * corruption.
 */
public class YamlStorage implements StorageManager {

    private final ZDiscord plugin;

    private File linksFile;
    private File statsFile;
    private File dataFile;

    private FileConfiguration linksConfig;
    private FileConfiguration statsConfig;
    private FileConfiguration dataConfig;

    // Write locks to prevent concurrent YAML corruption
    private final ReentrantReadWriteLock linksLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock statsLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();

    public YamlStorage(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void init() {
        linksFile = new File(plugin.getDataFolder(), "linked_accounts.yml");
        statsFile = new File(plugin.getDataFolder(), "leaderboard_data.yml");
        dataFile = new File(plugin.getDataFolder(), "plugin_data.yml");

        createIfMissing(linksFile);
        createIfMissing(statsFile);
        createIfMissing(dataFile);

        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        plugin.getLogger().info("Storage: Using YAML file storage (default)");
    }

    @Override
    public void shutdown() {
        saveFileLocked(linksConfig, linksFile, linksLock, "linked accounts");
        saveFileLocked(statsConfig, statsFile, statsLock, "leaderboard data");
        saveFileLocked(dataConfig, dataFile, dataLock, "plugin data");
    }

    @Override
    public String getTypeName() {
        return "YAML";
    }

    // ─── Links ──────────────────────────────────────

    @Override
    public Map<UUID, String> loadLinks() {
        Map<UUID, String> links = new ConcurrentHashMap<>();
        linksLock.readLock().lock();
        try {
            if (linksConfig.getConfigurationSection("links") != null) {
                for (String uuidStr : linksConfig.getConfigurationSection("links").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        String discordId = linksConfig.getString("links." + uuidStr);
                        if (discordId != null && !discordId.isEmpty()) {
                            links.put(uuid, discordId);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in linked_accounts.yml: " + uuidStr);
                    }
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
        saveFileAsync(linksConfig, linksFile, linksLock, "linked accounts");
    }

    @Override
    public void removeLink(UUID playerUUID) {
        linksLock.writeLock().lock();
        try {
            linksConfig.set("links." + playerUUID.toString(), null);
        } finally {
            linksLock.writeLock().unlock();
        }
        saveFileAsync(linksConfig, linksFile, linksLock, "linked accounts");
    }

    // ─── Stats ──────────────────────────────────────

    @Override
    public Map<UUID, Map<String, Long>> loadStats() {
        Map<UUID, Map<String, Long>> stats = new ConcurrentHashMap<>();
        statsLock.readLock().lock();
        try {
            if (statsConfig.getConfigurationSection("stats") != null) {
                for (String uuidStr : statsConfig.getConfigurationSection("stats").getKeys(false)) {
                    try {
                        UUID uuid = UUID.fromString(uuidStr);
                        Map<String, Long> playerStats = new ConcurrentHashMap<>();
                        var section = statsConfig.getConfigurationSection("stats." + uuidStr);
                        if (section != null) {
                            for (String stat : section.getKeys(false)) {
                                playerStats.put(stat, statsConfig.getLong("stats." + uuidStr + "." + stat));
                            }
                        }
                        stats.put(uuid, playerStats);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid UUID in leaderboard_data.yml: " + uuidStr);
                    }
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
        saveFileAsync(statsConfig, statsFile, statsLock, "leaderboard data");
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

    // ─── Key-Value Data ─────────────────────────────

    @Override
    public String getData(String key) {
        dataLock.readLock().lock();
        try {
            return dataConfig.getString("data." + key);
        } finally {
            dataLock.readLock().unlock();
        }
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
        saveFileAsync(dataConfig, dataFile, dataLock, "plugin data");
    }

    @Override
    public void setData(String key, int value) {
        dataLock.writeLock().lock();
        try {
            dataConfig.set("data." + key, value);
        } finally {
            dataLock.writeLock().unlock();
        }
        saveFileAsync(dataConfig, dataFile, dataLock, "plugin data");
    }

    // ─── Helpers ────────────────────────────────────

    private void createIfMissing(File file) {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create storage file " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private void saveFileLocked(FileConfiguration config, File file, ReentrantReadWriteLock lock, String label) {
        lock.writeLock().lock();
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save " + label + ": " + e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveFileAsync(FileConfiguration config, File file, ReentrantReadWriteLock lock, String label) {
        plugin.getPlatformAdapter().runAsync(() -> saveFileLocked(config, file, lock, label));
    }
}

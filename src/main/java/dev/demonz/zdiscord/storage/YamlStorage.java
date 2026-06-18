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
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class YamlStorage implements StorageManager {

    private final File dataFolder;
    private final Logger logger;
    private final PlatformAdapter platform;
    private final BooleanSupplier enabledSupplier;

    private File linksFile;
    private File statsFile;
    private File dataFile;
    private File activityFile;
    private File advancementsFile;
    private File followsFile;

    private FileConfiguration linksConfig;
    private FileConfiguration statsConfig;
    private FileConfiguration dataConfig;
    private FileConfiguration activityConfig;
    private FileConfiguration advancementsConfig;
    private FileConfiguration followsConfig;

    private final ReentrantReadWriteLock linksLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock statsLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock dataLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock activityLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock advancementsLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock followsLock = new ReentrantReadWriteLock();

    private volatile boolean running = true;
    private volatile boolean linksDirty, statsDirty, dataDirty, activityDirty, advancementsDirty, followsDirty;

    public YamlStorage(ZDiscord plugin) {
        this(plugin.getDataFolder(), plugin.getLogger(), plugin.getPlatformAdapter(),
                () -> plugin.isEnabled());
    }


    public YamlStorage(File dataFolder, Logger logger, PlatformAdapter platform,
                       BooleanSupplier enabledSupplier) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.platform = platform;
        this.enabledSupplier = enabledSupplier != null ? enabledSupplier : () -> true;
    }


    public YamlStorage(File dataFolder, Logger logger, PlatformAdapter platform) {
        this(dataFolder, logger, platform, () -> true);
    }

    @Override
    public void init() {
        linksFile = new File(dataFolder, "linked_accounts.yml");
        statsFile = new File(dataFolder, "leaderboard_data.yml");
        dataFile = new File(dataFolder, "plugin_data.yml");
        activityFile = new File(dataFolder, "player_activity.yml");
        advancementsFile = new File(dataFolder, "advancement_unlocks.yml");
        followsFile = new File(dataFolder, "player_follows.yml");

        createIfMissing(linksFile);
        createIfMissing(statsFile);
        createIfMissing(dataFile);
        createIfMissing(activityFile);
        createIfMissing(advancementsFile);
        createIfMissing(followsFile);

        linksConfig = YamlConfiguration.loadConfiguration(linksFile);
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        activityConfig = YamlConfiguration.loadConfiguration(activityFile);
        advancementsConfig = YamlConfiguration.loadConfiguration(advancementsFile);
        followsConfig = YamlConfiguration.loadConfiguration(followsFile);

        platform.runAsyncTimer(this::flushDirtyFiles, 100L, 100L);

        logger.info("Storage: YAML file storage");
    }

    private void flushDirtyFiles() {
        if (!running) return;
        if (linksDirty && saveFileLocked(linksConfig, linksFile, linksLock, "linked accounts")) linksDirty = false;
        if (statsDirty && saveFileLocked(statsConfig, statsFile, statsLock, "leaderboard data")) statsDirty = false;
        if (dataDirty && saveFileLocked(dataConfig, dataFile, dataLock, "plugin data")) dataDirty = false;
        if (activityDirty && saveFileLocked(activityConfig, activityFile, activityLock, "player activity")) activityDirty = false;
        if (advancementsDirty && saveFileLocked(advancementsConfig, advancementsFile, advancementsLock, "advancement unlocks")) advancementsDirty = false;
        if (followsDirty && saveFileLocked(followsConfig, followsFile, followsLock, "player follows")) followsDirty = false;
    }

    @Override
    public void shutdown() {
        running = false;


        if (linksDirty) saveFileLocked(linksConfig, linksFile, linksLock, "linked accounts");
        if (statsDirty) saveFileLocked(statsConfig, statsFile, statsLock, "leaderboard data");
        if (dataDirty) saveFileLocked(dataConfig, dataFile, dataLock, "plugin data");
        if (activityDirty) saveFileLocked(activityConfig, activityFile, activityLock, "player activity");
        if (advancementsDirty) saveFileLocked(advancementsConfig, advancementsFile, advancementsLock, "advancement unlocks");
        if (followsDirty) saveFileLocked(followsConfig, followsFile, followsLock, "player follows");
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
        dataLock.writeLock().lock();
        try {



            dataConfig.set("data." + key, value);
        } finally {
            dataLock.writeLock().unlock();
        }
        scheduleFlush(dataConfig, dataFile, dataLock, "plugin data");
    }



    @Override
    public void setLastSeen(UUID playerUUID, long millis) {
        String key = playerUUID.toString();
        activityLock.writeLock().lock();
        try {
            long current = activityConfig.getLong("activity." + key + ".lastSeen", 0L);
            if (millis > current) {
                activityConfig.set("activity." + key + ".lastSeen", millis);
            }
        } finally {
            activityLock.writeLock().unlock();
        }
        scheduleFlush(activityConfig, activityFile, activityLock, "player activity");
    }

    @Override
    public long getLastSeen(UUID playerUUID) {
        activityLock.readLock().lock();
        try {
            return activityConfig.getLong("activity." + playerUUID + ".lastSeen", 0L);
        } finally {
            activityLock.readLock().unlock();
        }
    }

    @Override
    public void setFirstJoin(UUID playerUUID, long millis) {
        String key = playerUUID.toString();
        activityLock.writeLock().lock();
        try {
            if (!activityConfig.contains("activity." + key + ".firstJoin")) {
                activityConfig.set("activity." + key + ".firstJoin", millis);
            }
        } finally {
            activityLock.writeLock().unlock();
        }
        scheduleFlush(activityConfig, activityFile, activityLock, "player activity");
    }

    @Override
    public long getFirstJoin(UUID playerUUID) {
        activityLock.readLock().lock();
        try {
            return activityConfig.getLong("activity." + playerUUID + ".firstJoin", 0L);
        } finally {
            activityLock.readLock().unlock();
        }
    }

    @Override
    public void incrementSessions(UUID playerUUID) {
        String key = playerUUID.toString();
        activityLock.writeLock().lock();
        try {
            long current = activityConfig.getLong("activity." + key + ".sessions", 0L);
            activityConfig.set("activity." + key + ".sessions", current + 1);
        } finally {
            activityLock.writeLock().unlock();
        }
        scheduleFlush(activityConfig, activityFile, activityLock, "player activity");
    }

    @Override
    public long getSessions(UUID playerUUID) {
        activityLock.readLock().lock();
        try {
            return activityConfig.getLong("activity." + playerUUID + ".sessions", 0L);
        } finally {
            activityLock.readLock().unlock();
        }
    }



    @Override
    public void recordAdvancementUnlock(UUID playerUUID, String advancementKey) {
        String player = playerUUID.toString();
        advancementsLock.writeLock().lock();
        try {
            String path = "players." + player + ".advancements." + advancementKey;
            if (!advancementsConfig.contains(path)) {
                advancementsConfig.set(path, System.currentTimeMillis());
            }
        } finally {
            advancementsLock.writeLock().unlock();
        }
        scheduleFlush(advancementsConfig, advancementsFile, advancementsLock,
                "advancement unlocks");
    }

    @Override
    public boolean recordAdvancementUnlockIfNew(UUID playerUUID, String advancementKey) {
        String player = playerUUID.toString();
        advancementsLock.writeLock().lock();
        try {
            String path = "players." + player + ".advancements." + advancementKey;
            if (advancementsConfig.contains(path)) {
                return false;
            }
            advancementsConfig.set(path, System.currentTimeMillis());
            scheduleFlush(advancementsConfig, advancementsFile, advancementsLock,
                    "advancement unlocks");
            return true;
        } finally {
            advancementsLock.writeLock().unlock();
        }
    }

    @Override
    public int getPlayerAdvancementCount(UUID playerUUID) {
        advancementsLock.readLock().lock();
        try {
            var section = advancementsConfig.getConfigurationSection(
                    "players." + playerUUID + ".advancements");
            return section == null ? 0 : section.getKeys(false).size();
        } finally {
            advancementsLock.readLock().unlock();
        }
    }

    @Override
    public int getAdvancementUnlockerCount(String advancementKey) {
        advancementsLock.readLock().lock();
        try {
            var players = advancementsConfig.getConfigurationSection("players");
            if (players == null) return 0;
            int count = 0;
            for (String player : players.getKeys(false)) {
                if (advancementsConfig.contains(
                        "players." + player + ".advancements." + advancementKey)) {
                    count++;
                }
            }
            return count;
        } finally {
            advancementsLock.readLock().unlock();
        }
    }

    @Override
    public int getAdvancementActivePlayerCount() {
        advancementsLock.readLock().lock();
        try {
            var players = advancementsConfig.getConfigurationSection("players");
            return players == null ? 0 : players.getKeys(false).size();
        } finally {
            advancementsLock.readLock().unlock();
        }
    }



    @Override
    public void addFollower(UUID playerUUID, String discordId) {
        followsLock.writeLock().lock();
        try {
            java.util.List<String> list = followsConfig.getStringList(
                    "followers." + playerUUID);
            if (!list.contains(discordId)) {
                list.add(discordId);
                followsConfig.set("followers." + playerUUID, list);
            }
            java.util.List<String> followed = followsConfig.getStringList(
                    "following." + discordId);
            if (!followed.contains(playerUUID.toString())) {
                followed.add(playerUUID.toString());
                followsConfig.set("following." + discordId, followed);
            }
        } finally {
            followsLock.writeLock().unlock();
        }
        scheduleFlush(followsConfig, followsFile, followsLock, "player follows");
    }

    @Override
    public void removeFollower(UUID playerUUID, String discordId) {
        followsLock.writeLock().lock();
        try {
            java.util.List<String> list = followsConfig.getStringList(
                    "followers." + playerUUID);
            if (list.remove(discordId)) {
                followsConfig.set("followers." + playerUUID, list);
            }
            java.util.List<String> followed = followsConfig.getStringList(
                    "following." + discordId);
            if (followed.remove(playerUUID.toString())) {
                followsConfig.set("following." + discordId, followed);
            }
        } finally {
            followsLock.writeLock().unlock();
        }
        scheduleFlush(followsConfig, followsFile, followsLock, "player follows");
    }

    @Override
    public java.util.Set<String> getFollowers(UUID playerUUID) {
        followsLock.readLock().lock();
        try {
            return new java.util.HashSet<>(followsConfig.getStringList(
                    "followers." + playerUUID));
        } finally {
            followsLock.readLock().unlock();
        }
    }

    @Override
    public java.util.Set<UUID> getFollowedPlayers(String discordId) {
        followsLock.readLock().lock();
        try {
            java.util.Set<UUID> out = new java.util.HashSet<>();
            for (String raw : followsConfig.getStringList("following." + discordId)) {
                try {
                    out.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return out;
        } finally {
            followsLock.readLock().unlock();
        }
    }

    @Override
    public boolean isFollowing(UUID playerUUID, String discordId) {
        return getFollowers(playerUUID).contains(discordId);
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


    private boolean saveFileLocked(FileConfiguration config, File file,
                                 ReentrantReadWriteLock lock, String label) {
        lock.writeLock().lock();
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            logger.severe("Failed to save " + label + ": " + e.getMessage());
            return false;
        } finally {
            lock.writeLock().unlock();
        }
    }


    private void scheduleFlush(FileConfiguration config, File file,
                               ReentrantReadWriteLock lock, String label) {
        if (platform == null || !enabledSupplier.getAsBoolean()) {
            saveFileLocked(config, file, lock, label);
            return;
        }
        if (config == linksConfig) linksDirty = true;
        else if (config == statsConfig) statsDirty = true;
        else if (config == dataConfig) dataDirty = true;
        else if (config == activityConfig) activityDirty = true;
        else if (config == advancementsConfig) advancementsDirty = true;
        else if (config == followsConfig) followsDirty = true;
    }
}

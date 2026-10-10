package dev.demonz.zdiscord.storage;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.platform.PlatformAdapter;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * File-backed storage. Every data section is its own yml file with its own
 * lock, and writes are batched into a periodic flush so we're not hammering
 * disk on every stat tick.
 *
 * The dirty flag lives inside the same lock as the save itself, otherwise a
 * writer can slip in between the snapshot and the flag reset and its change
 * is silently lost.
 */
public class YamlStorage implements StorageManager {

    private final File dataFolder;
    private final Logger logger;
    private final PlatformAdapter platform;
    private final BooleanSupplier enabledSupplier;
    private volatile boolean running = true;
    private PlatformAdapter.TaskHandle flushTimer;

    private final Section links = new Section("linked accounts");
    private final Section stats = new Section("leaderboard data");
    private final Section data = new Section("plugin data");
    private final Section activity = new Section("player activity");
    private final Section advancements = new Section("advancement unlocks");
    private final Section follows = new Section("player follows");

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
        links.load("linked_accounts.yml");
        stats.load("leaderboard_data.yml");
        data.load("plugin_data.yml");
        activity.load("player_activity.yml");
        advancements.load("advancement_unlocks.yml");
        follows.load("player_follows.yml");

        flushTimer = platform.scheduleAsyncTimer(this::flushDirtySections, 100L, 100L);
        logger.info("Storage: YAML file storage");
    }

    private void flushDirtySections() {
        if (!running) return;
        for (Section s : sections()) {
            s.flush();
        }
    }

    @Override
    public void shutdown() {
        running = false;
        if (flushTimer != null) flushTimer.cancel();
        for (Section s : sections()) {
            s.flush();
        }
    }

    @Override
    public int pendingWriteCount() {
        int n = 0;
        for (Section s : sections()) {
            if (s.dirty) n++;
        }
        return n;
    }

    private Section[] sections() {
        return new Section[]{links, stats, data, activity, advancements, follows};
    }

    @Override
    public String getTypeName() {
        return "YAML";
    }

    @Override
    public Map<UUID, String> loadLinks() {
        Map<UUID, String> out = new ConcurrentHashMap<>();
        links.lock.readLock().lock();
        try {
            var section = links.config.getConfigurationSection("links");
            if (section == null) return out;

            for (String uuidStr : section.getKeys(false)) {
                try {
                    String discordId = links.config.getString("links." + uuidStr);
                    if (discordId != null && !discordId.isEmpty()) {
                        out.put(UUID.fromString(uuidStr), discordId);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID in linked_accounts.yml: " + uuidStr);
                }
            }
        } finally {
            links.lock.readLock().unlock();
        }
        return out;
    }

    @Override
    public void saveLink(UUID playerUUID, String discordId) {
        links.write(cfg -> cfg.set("links." + playerUUID, discordId));
    }

    @Override
    public void removeLink(UUID playerUUID) {
        links.write(cfg -> cfg.set("links." + playerUUID, null));
    }

    @Override
    public Map<UUID, Map<String, Long>> loadStats() {
        Map<UUID, Map<String, Long>> out = new ConcurrentHashMap<>();
        stats.lock.readLock().lock();
        try {
            var section = stats.config.getConfigurationSection("stats");
            if (section == null) return out;

            for (String uuidStr : section.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    Map<String, Long> playerStats = new ConcurrentHashMap<>();
                    var statSection = stats.config.getConfigurationSection("stats." + uuidStr);
                    if (statSection != null) {
                        for (String stat : statSection.getKeys(false)) {
                            playerStats.put(stat, stats.config.getLong("stats." + uuidStr + "." + stat));
                        }
                    }
                    out.put(uuid, playerStats);
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid UUID in leaderboard_data.yml: " + uuidStr);
                }
            }
        } finally {
            stats.lock.readLock().unlock();
        }
        return out;
    }

    @Override
    public void saveStat(UUID playerUUID, String stat, long value) {
        stats.write(cfg -> cfg.set("stats." + playerUUID + "." + stat, value));
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopStats(String stat, int limit) {
        return loadStats().entrySet().stream()
                .filter(e -> e.getValue().containsKey(stat))
                .map(e -> Map.entry(e.getKey(), e.getValue().get(stat)))
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .toList();
    }

    @Override
    public String getData(String key) {
        return getData(key, null);
    }

    @Override
    public String getData(String key, String defaultValue) {
        data.lock.readLock().lock();
        try {
            return data.config.getString("data." + key, defaultValue);
        } finally {
            data.lock.readLock().unlock();
        }
    }

    @Override
    public int getDataInt(String key, int defaultValue) {
        data.lock.readLock().lock();
        try {
            return data.config.getInt("data." + key, defaultValue);
        } finally {
            data.lock.readLock().unlock();
        }
    }

    @Override
    public void setData(String key, String value) {
        data.write(cfg -> cfg.set("data." + key, value));
    }

    @Override
    public void setData(String key, int value) {
        data.write(cfg -> cfg.set("data." + key, value));
    }

    @Override
    public void setLastSeen(UUID playerUUID, long millis) {
        activity.write(cfg -> {
            long current = cfg.getLong("activity." + playerUUID + ".lastSeen", 0L);
            if (millis > current) {
                cfg.set("activity." + playerUUID + ".lastSeen", millis);
            }
        });
    }

    @Override
    public long getLastSeen(UUID playerUUID) {
        activity.lock.readLock().lock();
        try {
            return activity.config.getLong("activity." + playerUUID + ".lastSeen", 0L);
        } finally {
            activity.lock.readLock().unlock();
        }
    }

    @Override
    public void setFirstJoin(UUID playerUUID, long millis) {
        activity.write(cfg -> {
            if (!cfg.contains("activity." + playerUUID + ".firstJoin")) {
                cfg.set("activity." + playerUUID + ".firstJoin", millis);
            }
        });
    }

    @Override
    public long getFirstJoin(UUID playerUUID) {
        activity.lock.readLock().lock();
        try {
            return activity.config.getLong("activity." + playerUUID + ".firstJoin", 0L);
        } finally {
            activity.lock.readLock().unlock();
        }
    }

    @Override
    public void incrementSessions(UUID playerUUID) {
        activity.write(cfg -> {
            long current = cfg.getLong("activity." + playerUUID + ".sessions", 0L);
            cfg.set("activity." + playerUUID + ".sessions", current + 1);
        });
    }

    @Override
    public long getSessions(UUID playerUUID) {
        activity.lock.readLock().lock();
        try {
            return activity.config.getLong("activity." + playerUUID + ".sessions", 0L);
        } finally {
            activity.lock.readLock().unlock();
        }
    }

    @Override
    public void recordAdvancementUnlock(UUID playerUUID, String advancementKey) {
        advancements.write(cfg -> {
            String path = "players." + playerUUID + ".advancements." + advancementKey;
            if (!cfg.contains(path)) {
                cfg.set(path, System.currentTimeMillis());
            }
        });
    }

    @Override
    public boolean recordAdvancementUnlockIfNew(UUID playerUUID, String advancementKey) {
        return advancements.apply(cfg -> {
            String path = "players." + playerUUID + ".advancements." + advancementKey;
            if (cfg.contains(path)) return false;
            cfg.set(path, System.currentTimeMillis());
            return true;
        });
    }

    @Override
    public int getPlayerAdvancementCount(UUID playerUUID) {
        advancements.lock.readLock().lock();
        try {
            var section = advancements.config.getConfigurationSection(
                    "players." + playerUUID + ".advancements");
            return section == null ? 0 : section.getKeys(false).size();
        } finally {
            advancements.lock.readLock().unlock();
        }
    }

    @Override
    public int getAdvancementUnlockerCount(String advancementKey) {
        advancements.lock.readLock().lock();
        try {
            var players = advancements.config.getConfigurationSection("players");
            if (players == null) return 0;

            int count = 0;
            for (String player : players.getKeys(false)) {
                if (advancements.config.contains(
                        "players." + player + ".advancements." + advancementKey)) {
                    count++;
                }
            }
            return count;
        } finally {
            advancements.lock.readLock().unlock();
        }
    }

    @Override
    public int getAdvancementActivePlayerCount() {
        advancements.lock.readLock().lock();
        try {
            var players = advancements.config.getConfigurationSection("players");
            return players == null ? 0 : players.getKeys(false).size();
        } finally {
            advancements.lock.readLock().unlock();
        }
    }

    @Override
    public void addFollower(UUID playerUUID, String discordId) {
        follows.write(cfg -> {
            List<String> list = cfg.getStringList("followers." + playerUUID);
            if (!list.contains(discordId)) {
                list.add(discordId);
                cfg.set("followers." + playerUUID, list);
            }

            List<String> followed = cfg.getStringList("following." + discordId);
            if (!followed.contains(playerUUID.toString())) {
                followed.add(playerUUID.toString());
                cfg.set("following." + discordId, followed);
            }
        });
    }

    @Override
    public void removeFollower(UUID playerUUID, String discordId) {
        follows.write(cfg -> {
            List<String> list = cfg.getStringList("followers." + playerUUID);
            if (list.remove(discordId)) {
                cfg.set("followers." + playerUUID, list);
            }

            List<String> followed = cfg.getStringList("following." + discordId);
            if (followed.remove(playerUUID.toString())) {
                cfg.set("following." + discordId, followed);
            }
        });
    }

    @Override
    public Set<String> getFollowers(UUID playerUUID) {
        follows.lock.readLock().lock();
        try {
            return new HashSet<>(follows.config.getStringList("followers." + playerUUID));
        } finally {
            follows.lock.readLock().unlock();
        }
    }

    @Override
    public Set<UUID> getFollowedPlayers(String discordId) {
        follows.lock.readLock().lock();
        try {
            Set<UUID> out = new HashSet<>();
            for (String raw : follows.config.getStringList("following." + discordId)) {
                try {
                    out.add(UUID.fromString(raw));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return out;
        } finally {
            follows.lock.readLock().unlock();
        }
    }

    /**
     * Top followed players straight from the file, so the leaderboard doesn't
     * depend on who happens to have joined since the last restart.
     */
    @Override
    public List<Map.Entry<UUID, Integer>> getTopFollowedPlayers(int limit) {
        follows.lock.readLock().lock();
        try {
            var section = follows.config.getConfigurationSection("followers");
            if (section == null) return List.of();

            List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>();
            for (String uuidStr : section.getKeys(false)) {
                int size = follows.config.getStringList("followers." + uuidStr).size();
                if (size <= 0) continue;
                try {
                    ranked.add(Map.entry(UUID.fromString(uuidStr), size));
                } catch (IllegalArgumentException ignored) {
                }
            }
            ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            return ranked.subList(0, Math.min(limit, ranked.size()));
        } finally {
            follows.lock.readLock().unlock();
        }
    }

    @Override
    public boolean isFollowing(UUID playerUUID, String discordId) {
        return getFollowers(playerUUID).contains(discordId);
    }

    private class Section {
        final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
        final String label;
        File file;
        YamlConfiguration config;
        boolean dirty;

        Section(String label) {
            this.label = label;
        }

        void load(String fileName) {
            file = new File(dataFolder, fileName);
            createIfMissing(file);
            config = YamlConfiguration.loadConfiguration(file);
        }

        void write(Consumer<YamlConfiguration> change) {
            lock.writeLock().lock();
            try {
                change.accept(config);
                dirty = true;
            } finally {
                lock.writeLock().unlock();
            }
            if (!flushIsScheduled()) flush();
        }

        <T> T apply(Function<YamlConfiguration, T> change) {
            T result;
            lock.writeLock().lock();
            try {
                result = change.apply(config);
                dirty = true;
            } finally {
                lock.writeLock().unlock();
            }
            if (!flushIsScheduled()) flush();
            return result;
        }

        void flush() {
            lock.writeLock().lock();
            try {
                if (!dirty) return;
                try {
                    config.save(file);
                    dirty = false;
                } catch (IOException e) {
                    logger.severe("Failed to save " + label + ": " + e.getMessage());
                }
            } finally {
                lock.writeLock().unlock();
            }
        }

        private boolean flushIsScheduled() {
            return running && platform != null && enabledSupplier.getAsBoolean();
        }
    }

    private void createIfMissing(File file) {
        if (file.exists()) return;
        try {
            file.getParentFile().mkdirs();
            file.createNewFile();
        } catch (IOException e) {
            logger.severe("Failed to create storage file " + file.getName() + ": " + e.getMessage());
        }
    }
}

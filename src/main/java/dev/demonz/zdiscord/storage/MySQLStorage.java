package dev.demonz.zdiscord.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.demonz.zdiscord.ZDiscord;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


public class MySQLStorage implements StorageManager {

    private final ZDiscord plugin;
    private HikariDataSource dataSource;
    private final AtomicInteger pendingOps = new AtomicInteger(0);
    private volatile boolean shuttingDown = false;

    public MySQLStorage(ZDiscord plugin) {
        this.plugin = plugin;
    }

    private void runTrackedAsync(Runnable task) {
        pendingOps.incrementAndGet();
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                task.run();
            } finally {
                pendingOps.decrementAndGet();
            }
        });
    }

    @Override
    public void init() {
        String host = plugin.getConfigManager().getString("storage.mysql.host", "localhost");
        int port = plugin.getConfigManager().getInt("storage.mysql.port", 3306);
        String database = plugin.getConfigManager().getString("storage.mysql.database", "zdiscord");
        String username = plugin.getConfigManager().getString("storage.mysql.username", "root");
        String password = plugin.getConfigManager().getString("storage.mysql.password", "");

        boolean useSsl = plugin.getConfigManager().getBoolean("storage.mysql.use-ssl", true);
        String jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?autoReconnect=true"
                + (useSsl ? "&useSSL=true&requireSSL=true&verifyServerCertificate=true"
                          : "&useSSL=false&allowPublicKeyRetrieval=true");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setIdleTimeout(300000);
        config.setConnectionTimeout(10000);
        config.setMaxLifetime(600000);
        config.setPoolName("ZDiscord-HikariPool");


        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");


        config.setConnectionTestQuery("SELECT 1");
        config.setValidationTimeout(5000);
        config.setLeakDetectionThreshold(60000);

        dataSource = new HikariDataSource(config);

        createTables();
        plugin.getLogger().info("Storage: Connected to MySQL at " + host + ":" + port + "/" + database);
    }

    private void createTables() {
        try (Connection conn = dataSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zdiscord_links (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY, " +
                            "discord_id VARCHAR(20) NOT NULL, " +
                            "linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zdiscord_stats (" +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "stat_name VARCHAR(32) NOT NULL, " +
                            "stat_value BIGINT NOT NULL DEFAULT 0, " +
                            "PRIMARY KEY (player_uuid, stat_name)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zdiscord_data (" +
                            "data_key VARCHAR(128) PRIMARY KEY, " +
                            "data_value TEXT, " +
                            "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zdiscord_player_activity (" +
                            "player_uuid VARCHAR(36) PRIMARY KEY, " +
                            "last_seen BIGINT NOT NULL DEFAULT 0, " +
                            "first_join BIGINT NOT NULL DEFAULT 0, " +
                            "sessions BIGINT NOT NULL DEFAULT 0" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zdiscord_advancement_unlocks (" +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "advancement_key VARCHAR(255) NOT NULL, " +
                            "unlocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                            "PRIMARY KEY (player_uuid, advancement_key)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            stmt.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS zdiscord_player_follows (" +
                            "player_uuid VARCHAR(36) NOT NULL, " +
                            "discord_id VARCHAR(20) NOT NULL, " +
                            "PRIMARY KEY (player_uuid, discord_id)" +
                            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create MySQL tables: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        shuttingDown = true;
        try {
            if (!pendingOps.compareAndSet(0, 0)) {
                plugin.getLogger().info("Waiting for " + pendingOps.get() + " pending MySQL operations...");
                long deadline = System.currentTimeMillis() + 5000;
                while (pendingOps.get() > 0 && System.currentTimeMillis() < deadline) {
                    Thread.sleep(50);
                }
                if (pendingOps.get() > 0) {
                    plugin.getLogger().warning(pendingOps.get() + " MySQL ops did not complete within 5s");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("MySQL connection pool closed.");
        }
    }

    @Override
    public String getTypeName() {
        return "MySQL";
    }



    @Override
    public Map<UUID, String> loadLinks() {
        Map<UUID, String> links = new ConcurrentHashMap<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement("SELECT player_uuid, discord_id FROM zdiscord_links");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    links.put(UUID.fromString(rs.getString("player_uuid")), rs.getString("discord_id"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in zdiscord_links: " + rs.getString("player_uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load linked accounts from MySQL: " + e.getMessage());
        }
        return links;
    }

    @Override
    public void saveLink(UUID playerUUID, String discordId) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO zdiscord_links (player_uuid, discord_id) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE discord_id = VALUES(discord_id)")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, discordId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save link to MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public void removeLink(UUID playerUUID) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM zdiscord_links WHERE player_uuid = ?")) {
                ps.setString(1, playerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove link from MySQL: " + e.getMessage());
            }
        });
    }



    @Override
    public Map<UUID, Map<String, Long>> loadStats() {
        Map<UUID, Map<String, Long>> stats = new ConcurrentHashMap<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn
                        .prepareStatement("SELECT player_uuid, stat_name, stat_value FROM zdiscord_stats");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    stats.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                            .put(rs.getString("stat_name"), rs.getLong("stat_value"));
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in zdiscord_stats: " + rs.getString("player_uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to load stats from MySQL: " + e.getMessage());
        }
        return stats;
    }

    @Override
    public void saveStat(UUID playerUUID, String stat, long value) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO zdiscord_stats (player_uuid, stat_name, stat_value) VALUES (?, ?, ?) " +
                                    "ON DUPLICATE KEY UPDATE stat_value = VALUES(stat_value)")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, stat);
                ps.setLong(3, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to save stat to MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public List<Map.Entry<UUID, Long>> getTopStats(String stat, int limit) {
        List<Map.Entry<UUID, Long>> top = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT player_uuid, stat_value FROM zdiscord_stats " +
                                "WHERE stat_name = ? ORDER BY stat_value DESC LIMIT ?")) {
            ps.setString(1, stat);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        top.add(Map.entry(UUID.fromString(rs.getString("player_uuid")), rs.getLong("stat_value")));
                    } catch (IllegalArgumentException e) {

                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get top stats from MySQL: " + e.getMessage());
        }
        return top;
    }



    @Override
    public String getData(String key) {
        return getData(key, null);
    }

    @Override
    public String getData(String key, String defaultValue) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT data_value FROM zdiscord_data WHERE data_key = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("data_value");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get data key '" + key + "' from MySQL: " + e.getMessage());
        }
        return defaultValue;
    }

    @Override
    public int getDataInt(String key, int defaultValue) {
        String val = getData(key);
        if (val == null)
            return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public void setData(String key, String value) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO zdiscord_data (data_key, data_value) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE data_value = VALUES(data_value)")) {
                ps.setString(1, key);
                ps.setString(2, value);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to set data key '" + key + "' in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public void setData(String key, int value) {
        setData(key, String.valueOf(value));
    }



    @Override
    public void setLastSeen(UUID playerUUID, long millis) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO zdiscord_player_activity (player_uuid, last_seen) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE last_seen = GREATEST(last_seen, VALUES(last_seen))")) {
                ps.setString(1, playerUUID.toString());
                ps.setLong(2, millis);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to setLastSeen in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public long getLastSeen(UUID playerUUID) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT last_seen FROM zdiscord_player_activity WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("last_seen");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getLastSeen from MySQL: " + e.getMessage());
        }
        return 0L;
    }

    @Override
    public void setFirstJoin(UUID playerUUID, long millis) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO zdiscord_player_activity (player_uuid, first_join) VALUES (?, ?) " +
                                    "ON DUPLICATE KEY UPDATE first_join = IF(first_join = 0, VALUES(first_join), first_join)")) {
                ps.setString(1, playerUUID.toString());
                ps.setLong(2, millis);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to setFirstJoin in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public long getFirstJoin(UUID playerUUID) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT first_join FROM zdiscord_player_activity WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("first_join");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getFirstJoin from MySQL: " + e.getMessage());
        }
        return 0L;
    }

    @Override
    public void incrementSessions(UUID playerUUID) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO zdiscord_player_activity (player_uuid, sessions) VALUES (?, 1) " +
                                    "ON DUPLICATE KEY UPDATE sessions = sessions + 1")) {
                ps.setString(1, playerUUID.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to incrementSessions in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public long getSessions(UUID playerUUID) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT sessions FROM zdiscord_player_activity WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong("sessions");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getSessions from MySQL: " + e.getMessage());
        }
        return 0L;
    }



    @Override
    public void recordAdvancementUnlock(UUID playerUUID, String advancementKey) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT IGNORE INTO zdiscord_advancement_unlocks " +
                                    "(player_uuid, advancement_key) VALUES (?, ?)")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, advancementKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to recordAdvancementUnlock in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public boolean recordAdvancementUnlockIfNew(UUID playerUUID, String advancementKey) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "INSERT IGNORE INTO zdiscord_advancement_unlocks " +
                                "(player_uuid, advancement_key) VALUES (?, ?)")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, advancementKey);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to recordAdvancementUnlockIfNew in MySQL: " + e.getMessage());
            return false;
        }
    }

    @Override
    public int getPlayerAdvancementCount(UUID playerUUID) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM zdiscord_advancement_unlocks WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getPlayerAdvancementCount from MySQL: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getAdvancementUnlockerCount(String advancementKey) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM zdiscord_advancement_unlocks WHERE advancement_key = ?")) {
            ps.setString(1, advancementKey);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getAdvancementUnlockerCount from MySQL: " + e.getMessage());
        }
        return 0;
    }

    @Override
    public int getAdvancementActivePlayerCount() {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(DISTINCT player_uuid) FROM zdiscord_advancement_unlocks")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1);
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getAdvancementActivePlayerCount from MySQL: " + e.getMessage());
        }
        return 0;
    }



    @Override
    public void addFollower(UUID playerUUID, String discordId) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "INSERT IGNORE INTO zdiscord_player_follows (player_uuid, discord_id) VALUES (?, ?)")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, discordId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to addFollower in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public void removeFollower(UUID playerUUID, String discordId) {
        runTrackedAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                    PreparedStatement ps = conn.prepareStatement(
                            "DELETE FROM zdiscord_player_follows WHERE player_uuid = ? AND discord_id = ?")) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, discordId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to removeFollower in MySQL: " + e.getMessage());
            }
        });
    }

    @Override
    public java.util.Set<String> getFollowers(UUID playerUUID) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT discord_id FROM zdiscord_player_follows WHERE player_uuid = ?")) {
            ps.setString(1, playerUUID.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("discord_id"));
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getFollowers from MySQL: " + e.getMessage());
        }
        return out;
    }

    @Override
    public java.util.Set<UUID> getFollowedPlayers(String discordId) {
        java.util.Set<UUID> out = new java.util.HashSet<>();
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT player_uuid FROM zdiscord_player_follows WHERE discord_id = ?")) {
            ps.setString(1, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        out.add(UUID.fromString(rs.getString("player_uuid")));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to getFollowedPlayers from MySQL: " + e.getMessage());
        }
        return out;
    }

    @Override
    public boolean isFollowing(UUID playerUUID, String discordId) {
        try (Connection conn = dataSource.getConnection();
                PreparedStatement ps = conn.prepareStatement(
                        "SELECT COUNT(*) FROM zdiscord_player_follows " +
                                "WHERE player_uuid = ? AND discord_id = ?")) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, discordId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to isFollowing from MySQL: " + e.getMessage());
        }
        return false;
    }
}

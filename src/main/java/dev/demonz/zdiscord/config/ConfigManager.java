package dev.demonz.zdiscord.config;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;

/**
 * Manages plugin configuration files.
 */
public class ConfigManager {

    private final ZDiscord plugin;
    private final File configFile;
    private FileConfiguration config;

    public ConfigManager(ZDiscord plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

        saveDefaultConfig();
        reload();
        checkConfigVersion();
    }

    private void checkConfigVersion() {
        int latestVersion = 2; // Increment this when config structure changes
        int currentVersion = config.getInt("config-version", 0);

        if (currentVersion < latestVersion) {
            plugin.getLogger().warning(
                    "Detected outdated configuration (v" + currentVersion + "). Updating to v" + latestVersion + "...");

            // Backup old config
            File backupFile = new File(plugin.getDataFolder(), "config-backup-" + System.currentTimeMillis() + ".yml");
            try {
                java.nio.file.Files.move(configFile.toPath(), backupFile.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                plugin.getLogger().warning("Old configuration backed up to: " + backupFile.getName());
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to backup config file: " + e.getMessage());
                plugin.getLogger().severe("Aborting config upgrade to prevent data loss.");
                return;
            }

            // Create new config
            saveDefaultConfig();
            reload();
            plugin.getLogger().info("Fresh configuration generated. Please re-apply your settings from the backup.");
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void saveDefaultConfig() {
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            reload();
        }
        return config;
    }

    public String getString(String path) {
        return config.getString(path, "");
    }

    public String getString(String path, String def) {
        return config.getString(path, def);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path, false);
    }

    public boolean getBoolean(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public int getInt(String path) {
        return config.getInt(path, 0);
    }

    public int getInt(String path, int def) {
        return config.getInt(path, def);
    }

    public double getDouble(String path) {
        return config.getDouble(path, 0.0);
    }

    public double getDouble(String path, double def) {
        return config.getDouble(path, def);
    }

    public List<String> getStringList(String path) {
        return config.getStringList(path);
    }
}

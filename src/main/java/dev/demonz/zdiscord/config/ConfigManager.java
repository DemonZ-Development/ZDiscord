package dev.demonz.zdiscord.config;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

public class ConfigManager {

    public static final int CURRENT_VERSION = 16;

    private static final List<String> LEGACY_AVATAR_DEFAULTS = List.of(
            "https://mc-heads.net/avatar/%uuid%/128",
            "https://crafatar.com/avatars/%uuid%?overlay=true");

    private final File dataFolder;
    private final Logger logger;
    private final Supplier<InputStream> defaultResource;
    private final File configFile;
    private volatile FileConfiguration config;

    public ConfigManager(ZDiscord plugin) {
        this(plugin.getDataFolder(), plugin.getLogger(), () -> plugin.getResource("config.yml"));
    }

    public ConfigManager(File dataFolder, Logger logger, Supplier<InputStream> defaultResource) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.defaultResource = defaultResource;
        this.configFile = new File(dataFolder, "config.yml");
        saveDefaultConfig();
        reload();
        migrateIfNeeded();
        sanitizePlaceholders();
    }

    private void saveDefaultConfig() {
        if (configFile.exists()) return;
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("Could not create data folder: " + dataFolder);
        }
        if (defaultResource == null) return;

        try (InputStream in = defaultResource.get()) {
            if (in != null) {
                Files.copy(in, configFile.toPath());
            }
        } catch (IOException e) {
            logger.warning("Failed to copy default config: " + e.getMessage());
        }
    }

    private void migrateIfNeeded() {
        int current = config.getInt("config-version", 0);
        if (current >= CURRENT_VERSION) return;

        logger.info("Configuration is at v" + current
                + "; current schema is v" + CURRENT_VERSION + ". Merging in any new keys.");

        try (InputStream defStream = defaultResource == null ? null : defaultResource.get()) {
            if (defStream == null) {
                logger.warning("Default config.yml not found in JAR; skipping migration.");
                return;
            }

            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
            config.options().copyDefaults(false);
            if (current < 8 && LEGACY_AVATAR_DEFAULTS.contains(config.getString("chat.avatar-url", ""))) {
                config.set("chat.avatar-url", "auto");
                logger.info("chat.avatar-url switched to \"auto\" (per-player skin avatars).");
            }
            for (String key : defaults.getKeys(true)) {
                if (!config.isSet(key)) {
                    if (defaults.isConfigurationSection(key)) {
                        config.createSection(key);
                    } else {
                        config.set(key, defaults.get(key));
                    }
                }
            }
            config.options().copyDefaults(true);
            config.set("config-version", CURRENT_VERSION);
            config.save(configFile);
            logger.info("Configuration migration complete. New defaults have been added; existing values preserved.");
        } catch (IOException e) {
            logger.severe("Failed to write migrated config: " + e.getMessage());
            try {
                Files.copy(configFile.toPath(),
                        new File(configFile.getParentFile(), "config.yml.bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                logger.severe("A backup of your existing config has been written to config.yml.bak");
            } catch (IOException backupError) {
                logger.severe("Could not back up config.yml: " + backupError.getMessage());
            }
        } catch (Exception e) {
            logger.warning("Failed to read default config for migration: " + e.getMessage());
        } finally {
            config.options().copyDefaults(true);
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        try (InputStream defStream = defaultResource == null ? null : defaultResource.get()) {
            if (defStream != null) {
                YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8));
                config.setDefaults(defaults);
                config.options().copyDefaults(true);
            }
        } catch (Exception e) {
            logger.warning("Failed to load default config: " + e.getMessage());
        }
    }

    public FileConfiguration getConfig() {
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

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            logger.severe("Failed to save config: " + e.getMessage());
        }
    }

    private void sanitizePlaceholders() {
        List<String> roles = config.getStringList("tickets.support-roles");
        if (roles.isEmpty()) return;

        List<String> clean = new ArrayList<>();
        boolean changed = false;
        for (String role : roles) {
            if (role == null || role.isBlank() || role.startsWith("YOUR_")) {
                changed = true;
            } else {
                clean.add(role);
            }
        }

        if (changed) {
            config.set("tickets.support-roles", clean);
            save();
        }
    }
}

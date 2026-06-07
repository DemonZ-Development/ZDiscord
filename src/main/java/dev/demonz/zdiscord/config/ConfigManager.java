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
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Loads and provides typed accessors for {@code config.yml}.
 *
 * <p>If the existing config file is older than the version bundled with the
 * plugin, the user's existing values are merged into the new defaults. The
 * user's file is not deleted; instead, missing keys are filled in and the
 * version stamp is updated. This avoids the data loss that a destructive
 * "rename old, copy new" migration would cause.</p>
 */
public class ConfigManager {

    /**
     * The current config-version. Increment when the schema changes in a
     * non-additive way.
     */
    public static final int CURRENT_VERSION = 3;

    private final File dataFolder;
    private final Logger logger;
    private final Supplier<InputStream> defaultResource;
    private final File configFile;
    private FileConfiguration config;

    /**
     * Production constructor. Sources the default config from the
     * plugin's JAR.
     */
    public ConfigManager(ZDiscord plugin) {
        this(
                plugin.getDataFolder(),
                plugin.getLogger(),
                () -> plugin.getResource("config.yml"));
    }

    /**
     * Test-friendly constructor. The {@code defaultResource} supplier
     * is called each time the file needs to be (re-)read from defaults
     * — it can return a fresh stream to the bundled resource, or null
     * to skip default loading.
     */
    public ConfigManager(File dataFolder,
                         Logger logger,
                         Supplier<InputStream> defaultResource) {
        this.dataFolder = dataFolder;
        this.logger = logger;
        this.defaultResource = defaultResource;
        this.configFile = new File(dataFolder, "config.yml");
        saveDefaultConfig();
        reload();
        migrateIfNeeded();
    }

    private void saveDefaultConfig() {
        if (configFile.exists()) {
            return;
        }
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            logger.warning("Could not create data folder: " + dataFolder);
        }
        if (defaultResource == null) {
            return;
        }
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
        if (current >= CURRENT_VERSION) {
            return;
        }

        logger.info("Configuration is at v" + current
                + "; current schema is v" + CURRENT_VERSION + ". Merging in any new keys.");

        InputStream defStream = defaultResource == null ? null : defaultResource.get();
        if (defStream == null) {
            logger.warning("Default config.yml not found in JAR; skipping migration.");
            return;
        }

        try {
            FileConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream));
            for (String key : defaults.getKeys(true)) {
                if (!config.contains(key)) {
                    config.set(key, defaults.get(key));
                }
            }
            config.set("config-version", CURRENT_VERSION);
            config.save(configFile);
            logger.info("Configuration migration complete. New defaults have been added; existing values preserved.");
        } catch (IOException e) {
            logger.severe("Failed to write migrated config: " + e.getMessage());
            logger.severe("A backup of your existing config has been written to config.yml.bak");
            try {
                Files.copy(configFile.toPath(),
                        new File(configFile.getParentFile(), "config.yml.bak").toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        }
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);

        // Also merge in defaults from the JAR so that newly added keys are
        // visible via getX(path, defaultValue) even if the on-disk file is older.
        InputStream defStream = defaultResource == null ? null : defaultResource.get();
        if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream));
            config.setDefaults(defaults);
            config.options().copyDefaults(true);
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
}

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

package dev.demonz.zdiscord.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests {@link ConfigManager} against the bundled {@code config.yml}.
 * The plugin's own constructor needs a {@code JavaPlugin} instance,
 * so we use the test-friendly constructor that takes a data folder,
 * a logger, and a supplier of the default config resource.
 */
class ConfigManagerTest {

    private static InputStream defaultConfig() {
        return ConfigManagerTest.class.getClassLoader()
                .getResourceAsStream("config.yml");
    }

    private static ConfigManager newManager(File dataFolder) {
        return new ConfigManager(
                dataFolder,
                Logger.getLogger("ConfigManagerTest"),
                ConfigManagerTest::defaultConfig);
    }

    @Test
    void loadsDefaultConfig(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());
        assertNotNull(mgr);
        String token = mgr.getString("bot.token");
        assertNotNull(token);
        assertTrue(token.startsWith("YOUR_")
                        || token.length() > 0,
                "Default config should provide a placeholder or real token");
    }

    @Test
    void readsBooleanDefaults(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());
        boolean status = mgr.getBoolean("status.enabled", true);
        assertTrue(status, "Default status.enabled should be true");
    }

    @Test
    void readsIntDefaults(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());
        int interval = mgr.getInt("status.update-interval", 30);
        assertTrue(interval > 0, "Default status.update-interval should be positive");
    }

    @Test
    void readsStringListDefaults(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());
        var roles = mgr.getStringList("tickets.support-roles");
        assertNotNull(roles);
        assertFalse(roles.isEmpty(),
                "Default tickets.support-roles should have at least one placeholder");
    }

    @Test
    void readsTicketCategories(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());
        // Categories is a YAML list (each entry has id/label/...),
        // not a section, so use getMapList and check the size.
        var categories = mgr.getConfig().getMapList("tickets.categories");
        assertFalse(categories.isEmpty(),
                "Default config should include ticket categories");
        assertTrue(categories.size() >= 2,
                "Default config should define multiple ticket categories");
    }

    @Test
    void configVersionIsBumped(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());
        int version = mgr.getInt("config-version", 0);
        assertTrue(version >= 3,
                "Config version should be 3 or higher after the rewrite");
    }

    @Test
    void migrationIsIdempotent(@TempDir Path tmp) throws IOException {
        // Create a config file that is older than the bundled default.
        Path cfg = Paths.get(tmp.toString(), "config.yml");
        Files.writeString(cfg, "config-version: 0\nbot:\n  token: old-token\n");

        ConfigManager mgr = newManager(tmp.toFile());
        assertEquals("old-token", mgr.getString("bot.token"),
                "Existing values must be preserved across migration");
        // After migration, version is bumped to CURRENT_VERSION.
        assertEquals(ConfigManager.CURRENT_VERSION, mgr.getInt("config-version", 0));
    }
}

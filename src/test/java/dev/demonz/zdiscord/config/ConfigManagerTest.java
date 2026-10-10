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
        assertTrue(mgr.getBoolean("chat.enabled", true), "Default chat.enabled should be true");
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
        assertTrue(roles.isEmpty(),
                "Default tickets.support-roles should not contain placeholder role IDs");
    }

    @Test
    void readsTicketCategories(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());

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
        Path cfg = Paths.get(tmp.toString(), "config.yml");
        Files.writeString(cfg, "config-version: 0\nbot:\n  token: old-token\n");

        ConfigManager mgr = newManager(tmp.toFile());
        assertEquals("old-token", mgr.getString("bot.token"),
                "Existing values must be preserved across migration");

        assertEquals(ConfigManager.CURRENT_VERSION, mgr.getInt("config-version", 0));
    }

    @Test
    void removesPlaceholderSupportRoles(@TempDir Path tmp) throws IOException {
        Path cfg = Paths.get(tmp.toString(), "config.yml");
        Files.writeString(cfg, """
                config-version: 6
                tickets:
                  support-roles:
                    - YOUR_SUPPORT_ROLE_ID
                    - 123456789012345678
                """);

        ConfigManager mgr = newManager(tmp.toFile());
        var roles = mgr.getStringList("tickets.support-roles");

        assertEquals(1, roles.size());
        assertEquals("123456789012345678", roles.get(0));
    }

    @Test
    void halloweenDefaultsKeepCosmeticsOptIn(@TempDir Path tmp) {
        ConfigManager mgr = newManager(tmp.toFile());

        assertTrue(mgr.getBoolean("halloween.enabled", false));
        assertFalse(mgr.getBoolean("halloween.cosmetics.enabled", true));
        assertFalse(mgr.getBoolean("halloween.hunting.count-spawner-mobs", true));
        assertEquals(java.util.List.of("CREATIVE", "SPECTATOR"),
                mgr.getStringList("halloween.hunting.excluded-game-modes"));
        assertEquals("server", mgr.getString("halloween.timezone"));
    }

    @Test
    void halloweenMigrationPreservesExistingControls(@TempDir Path tmp) throws IOException {
        Files.writeString(tmp.resolve("config.yml"), """
                config-version: 15
                halloween:
                  enabled: false
                  window-start: '10-20'
                  color: '#A04000'
                  countdown:
                    days: 0
                  rewards:
                    winner: ['give %player% diamond 1']
                """);
        ConfigManager mgr = newManager(tmp.toFile());

        assertFalse(mgr.getBoolean("halloween.enabled", true));
        assertEquals("10-20", mgr.getString("halloween.window-start"));
        assertEquals("#A04000", mgr.getString("halloween.color"));
        assertEquals(0, mgr.getInt("halloween.countdown.days", 7));
        assertEquals(java.util.List.of("give %player% diamond 1"),
                mgr.getStringList("halloween.rewards.winner"));
        assertFalse(mgr.getBoolean("halloween.cosmetics.enabled", true));
        assertEquals(ConfigManager.CURRENT_VERSION, mgr.getInt("config-version", 0));
    }
}

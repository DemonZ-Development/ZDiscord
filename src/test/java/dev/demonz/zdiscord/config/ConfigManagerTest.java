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

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import dev.demonz.zdiscord.ZDiscord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {

    private ServerMock server;
    private ZDiscord plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(ZDiscord.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    void loadsDefaultConfig() {
        assertNotNull(plugin.getConfigManager());
        String token = plugin.getConfigManager().getString("bot.token");
        assertNotNull(token);
        assertTrue(token.startsWith("YOUR_")
                || token.length() > 0,
                "Default config should provide a placeholder or real token");
    }

    @Test
    void readsBooleanDefaults() {
        boolean status = plugin.getConfigManager().getBoolean("status.enabled", true);
        assertTrue(status, "Default status.enabled should be true");
    }

    @Test
    void readsIntDefaults() {
        int interval = plugin.getConfigManager().getInt("status.update-interval", 30);
        assertTrue(interval > 0, "Default status.update-interval should be positive");
    }

    @Test
    void readsStringListDefaults() {
        var roles = plugin.getConfigManager().getStringList("tickets.support-roles");
        assertNotNull(roles);
        assertFalse(roles.isEmpty(),
                "Default tickets.support-roles should have at least one placeholder");
    }

    @Test
    void readsTicketCategories() {
        var categories = plugin.getConfigManager().getConfig()
                .getConfigurationSection("tickets.categories");
        assertNotNull(categories,
                "Default config should include ticket categories");
        assertTrue(categories.getKeys(false).size() >= 2,
                "Default config should define multiple ticket categories");
    }

    @Test
    void configVersionIsBumped() {
        int version = plugin.getConfigManager().getInt("config-version", 0);
        assertTrue(version >= 3,
                "Config version should be 3 or higher after the rewrite");
    }
}

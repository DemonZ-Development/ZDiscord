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

package dev.demonz.zdiscord.storage;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import dev.demonz.zdiscord.ZDiscord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlStorageTest {

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
    void storageInitialises() {
        StorageManager storage = plugin.getStorageManager();
        assertNotNull(storage);
        assertEquals("YAML", storage.getTypeName());
    }

    @Test
    void canRoundTripKeyValueData() {
        StorageManager storage = plugin.getStorageManager();
        storage.setData("test-key", "hello");
        assertEquals("hello", storage.getData("test-key", "default"));
    }

    @Test
    void canRoundTripKeyValueInt() {
        StorageManager storage = plugin.getStorageManager();
        storage.setData("test-int", 42);
        assertEquals(42, storage.getDataInt("test-int", 0));
    }

    @Test
    void canSaveAndLoadLinks() {
        StorageManager storage = plugin.getStorageManager();
        UUID uuid = UUID.randomUUID();
        storage.saveLink(uuid, "123456789012345678");
        var loaded = storage.loadLinks();
        assertTrue(loaded.containsKey(uuid),
                "Saved link should be loaded back");
        assertEquals("123456789012345678", loaded.get(uuid));
    }

    @Test
    void canRemoveLink() {
        StorageManager storage = plugin.getStorageManager();
        UUID uuid = UUID.randomUUID();
        storage.saveLink(uuid, "987654321098765432");
        storage.removeLink(uuid);
        var loaded = storage.loadLinks();
        assertTrue(!loaded.containsKey(uuid),
                "Removed link should not be loaded");
    }

    @Test
    void canSaveAndLoadStats() {
        StorageManager storage = plugin.getStorageManager();
        UUID uuid = UUID.randomUUID();
        storage.saveStat(uuid, "kills", 5);
        storage.saveStat(uuid, "deaths", 3);

        var stats = storage.loadStats();
        var playerStats = stats.get(uuid);
        assertNotNull(playerStats);
        assertEquals(5, playerStats.get("kills"));
        assertEquals(3, playerStats.get("deaths"));
    }

    @Test
    void topStatsIsSorted() {
        StorageManager storage = plugin.getStorageManager();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        storage.saveStat(a, "kills", 10);
        storage.saveStat(b, "kills", 50);
        storage.saveStat(c, "kills", 25);

        var top = storage.getTopStats("kills", 3);
        assertEquals(3, top.size());
        assertEquals(b, top.get(0).getKey());
        assertEquals(50, top.get(0).getValue());
    }
}

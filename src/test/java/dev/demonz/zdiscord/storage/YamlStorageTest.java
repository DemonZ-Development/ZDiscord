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

import dev.demonz.zdiscord.testsupport.SyncPlatformAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlStorageTest {

    private static YamlStorage newStorage(File dataFolder) {
        YamlStorage storage = new YamlStorage(
                dataFolder,
                Logger.getLogger("YamlStorageTest"),
                SyncPlatformAdapter.INSTANCE);
        storage.init();
        return storage;
    }

    @Test
    void storageInitialises(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
        assertNotNull(storage);
        assertEquals("YAML", storage.getTypeName());
    }

    @Test
    void canRoundTripKeyValueData(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
        storage.setData("test-key", "hello");
        assertEquals("hello", storage.getData("test-key", "default"));
    }

    @Test
    void canRoundTripKeyValueInt(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
        storage.setData("test-int", 42);
        assertEquals(42, storage.getDataInt("test-int", 0));
    }

    @Test
    void canSaveAndLoadLinks(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
        UUID uuid = UUID.randomUUID();
        storage.saveLink(uuid, "123456789012345678");
        var loaded = storage.loadLinks();
        assertTrue(loaded.containsKey(uuid),
                "Saved link should be loaded back");
        assertEquals("123456789012345678", loaded.get(uuid));
    }

    @Test
    void canRemoveLink(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
        UUID uuid = UUID.randomUUID();
        storage.saveLink(uuid, "987654321098765432");
        storage.removeLink(uuid);
        var loaded = storage.loadLinks();
        assertTrue(!loaded.containsKey(uuid),
                "Removed link should not be loaded");
    }

    @Test
    void canSaveAndLoadStats(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
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
    void topStatsIsSorted(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
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

    @Test
    void shutdownFlushesFiles(@TempDir Path tmp) {
        YamlStorage storage = newStorage(tmp.toFile());
        UUID uuid = UUID.randomUUID();
        storage.saveLink(uuid, "1");
        storage.saveStat(uuid, "kills", 7);
        storage.setData("foo", "bar");
        storage.shutdown();

        YamlStorage reopened = newStorage(tmp.toFile());
        assertEquals("1", reopened.loadLinks().get(uuid));
        assertEquals(7L, (long) reopened.loadStats().get(uuid).get("kills"));
        assertEquals("bar", reopened.getData("foo", ""));
    }
}

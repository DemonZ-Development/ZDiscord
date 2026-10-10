package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.YamlStorage;
import dev.demonz.zdiscord.testsupport.SyncPlatformAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AccountLinksTest {
    @TempDir Path folder;

    private YamlStorage storage() {
        YamlStorage storage = new YamlStorage(folder.toFile(), Logger.getLogger("links-test"), SyncPlatformAdapter.INSTANCE);
        storage.init();
        return storage;
    }

    @Test void generatingAgainInvalidatesThePreviousCode() {
        var storage = storage();
        var links = new AccountLinks(storage);
        UUID player = UUID.randomUUID();
        String old = links.code(player, false), current = links.code(player, false);
        assertNull(links.redeem("A", old));
        assertNotNull(links.redeem("B", current));
        assertNull(links.player("A"));
        assertEquals(player, links.player("B"));
        assertEquals("B", links.discord(player));
        assertNull(links.redeem("C", current));
    }

    @Test void rejectedLoginsReuseAnUnexpiredCodeWithoutAnOnlinePlayer() {
        AtomicLong now = new AtomicLong();
        var links = new AccountLinks(storage(), now::get);
        UUID player = UUID.randomUUID();
        String first = links.code(player, true);
        assertEquals(first, links.code(player, true));
        now.set(AccountLinks.EXPIRY_MILLIS);
        assertNull(links.redeem("A", first));
        String next = links.code(player, true);
        assertNotEquals(first, next);
        assertNotNull(links.redeem("A", next));
        assertNull(links.code(player, true));
    }

    @Test void relinkingDiscordRemovesOnlyItsPreviousMinecraftLink() {
        var storage = storage();
        var links = new AccountLinks(storage);
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        links.redeem("discord", links.code(a, false));
        links.redeem("discord", links.code(b, false));
        assertNull(links.discord(a));
        assertEquals(b, links.player("discord"));
        assertEquals(java.util.Map.of(b, "discord"), storage.loadLinks());
        var restarted = new AccountLinks(storage);
        assertEquals(b, restarted.player("discord"));
        assertEquals("discord", restarted.unlink(b));
        assertNull(restarted.player("discord"));
        assertTrue(storage.loadLinks().isEmpty());
    }

    @Test void concurrentRedemptionHasOnlyOneWinner() throws Exception {
        var links = new AccountLinks(storage());
        UUID player = UUID.randomUUID();
        String code = links.code(player, false);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var results = pool.invokeAll(List.of(() -> links.redeem("A", code), () -> links.redeem("B", code)));
            int winners = 0;
            for (var result : results) if (result.get() != null) winners++;
            assertEquals(1, winners);
            assertEquals(player, links.player(links.discord(player)));
        } finally { pool.shutdownNow(); }
    }

    @Test void legacyDuplicateDiscordIdsAreReconciledOnLoad() {
        var storage = storage();
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        storage.saveLink(a, "duplicate"); storage.saveLink(b, "duplicate");
        var links = new AccountLinks(storage);
        assertEquals(b, links.player("duplicate"));
        assertNull(links.discord(a));
        assertEquals(java.util.Map.of(b,"duplicate"), storage.loadLinks());
    }
}

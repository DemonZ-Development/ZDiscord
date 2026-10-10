package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.StorageManager;
import dev.demonz.zdiscord.storage.YamlStorage;
import dev.demonz.zdiscord.testsupport.SyncPlatformAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class FollowerStoreTest {
    @Test void persistedFollowersAreReadBeforeMutations(@TempDir Path folder) {
        var storage = new YamlStorage(folder.toFile(),Logger.getLogger("followers-test"),SyncPlatformAdapter.INSTANCE);
        storage.init(); UUID player = UUID.randomUUID();
        storage.addFollower(player,"old");
        var state = new FollowerStore(storage);
        assertTrue(state.contains(player,"old"));
        assertTrue(state.follow(player,"new"));
        assertEquals(Set.of("old","new"),state.followers(player));
        assertFalse(state.follow(player,"new"));
        assertTrue(state.unfollow(player,"old"));
        assertFalse(state.contains(player,"old"));
        assertEquals(Set.of(player),state.followedPlayers("new"));
        storage.shutdown();
        var restarted = new YamlStorage(folder.toFile(),Logger.getLogger("followers-test"),SyncPlatformAdapter.INSTANCE);
        restarted.init();
        assertTrue(new FollowerStore(restarted).contains(player,"new"));
    }

    @Test void bufferedWritesRemainImmediatelyVisibleInBothDirections() {
        UUID player = UUID.randomUUID();
        StorageManager buffered = (StorageManager) Proxy.newProxyInstance(StorageManager.class.getClassLoader(),
                new Class<?>[] {StorageManager.class}, (proxy, method, args) -> switch(method.getName()) {
                    case "getFollowers" -> Set.of("stored");
                    case "getFollowedPlayers" -> args[0].equals("stored") ? Set.of(player) : Set.of();
                    default -> null;
                });
        var state = new FollowerStore(buffered);
        assertTrue(state.follow(player,"new"));
        assertEquals(Set.of(player),state.followedPlayers("new"));
        assertTrue(state.unfollow(player,"stored"));
        assertTrue(state.followedPlayers("stored").isEmpty());
        assertEquals(Set.of("new"),state.followers(player));
    }
}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.StorageManager;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class LeaderboardConcurrencyTest {
    @Test void concurrentUpdatesPersistTheFinalValue() throws Exception {
        AtomicLong persisted = new AtomicLong();
        StorageManager storage = (StorageManager) Proxy.newProxyInstance(StorageManager.class.getClassLoader(),
                new Class<?>[] {StorageManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("saveStat")) persisted.set((Long)args[2]);
                    return null;
                });
        var board = new LeaderboardModule(storage); UUID player = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var calls = new ArrayList<Callable<Void>>();
            for (int i=0;i<500;i++) calls.add(() -> { board.incrementStat(player,"kills"); return null; });
            for (var result : pool.invokeAll(calls)) result.get();
            assertEquals(500L,board.getStat(player,"kills"));
            assertEquals(500L,persisted.get());
        } finally { pool.shutdownNow(); }
    }

    @Test void aSlowFirstWriteCannotArriveAfterTheSecondWrite() throws Exception {
        AtomicLong persisted = new AtomicLong();
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1), second = new CountDownLatch(1);
        StorageManager storage = (StorageManager) Proxy.newProxyInstance(StorageManager.class.getClassLoader(),
                new Class<?>[] {StorageManager.class}, (proxy, method, args) -> {
                    if (method.getName().equals("saveStat")) {
                        long value = (Long)args[2];
                        if(value==1L) { entered.countDown(); assertTrue(release.await(3,TimeUnit.SECONDS)); }
                        if(value==2L) second.countDown();
                        persisted.set(value);
                    }
                    return null;
                });
        var board = new LeaderboardModule(storage); UUID player = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() -> board.incrementStat(player,"kills"));
            assertTrue(entered.await(3,TimeUnit.SECONDS));
            var next = pool.submit(() -> board.incrementStat(player,"kills"));
            assertFalse(second.await(100,TimeUnit.MILLISECONDS));
            release.countDown();
            first.get(3,TimeUnit.SECONDS); next.get(3,TimeUnit.SECONDS);
            assertEquals(2L,persisted.get());
            assertEquals(2L,board.getStat(player,"kills"));
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}

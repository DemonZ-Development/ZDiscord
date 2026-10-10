package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class PlaytimeTrackerTest {
    @Test void concurrentFlushesCreditAnIntervalOnce() throws Exception {
        var tracker = new PlaytimeTracker();
        UUID player = UUID.randomUUID(); tracker.join(player, 0L);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var calls = new ArrayList<Callable<Long>>();
            for (int i = 0; i < 100; i++) calls.add(() -> tracker.claim(player, 60_000L));
            long total = 0L;
            for (var result : pool.invokeAll(calls)) total += result.get();
            assertEquals(60L,total);
            assertEquals(1L,tracker.quit(player,61_000L));
            assertEquals(0L,tracker.claim(player,120_000L));
        } finally { pool.shutdownNow(); }
    }

    @Test void repeatedSubsecondFlushesRetainElapsedTime() {
        var tracker = new PlaytimeTracker(); UUID player = UUID.randomUUID();
        tracker.join(player,100L);
        assertEquals(1L,tracker.claim(player,1900L));
        assertEquals(1L,tracker.claim(player,2200L));
        assertEquals(0L,tracker.claim(player,2100L));
        assertEquals(1L,tracker.quit(player,3100L));
        tracker.join(player,4000L);
        assertEquals(1L,tracker.claim(player,5000L));
    }
}

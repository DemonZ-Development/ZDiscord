package dev.demonz.zdiscord.modules;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class TicketCountsTest {
    @Test void concurrentRequestsShareTheLinkedAccountLimit() throws Exception {
        var counts = new TicketCounts();
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> calls = List.of(
                    () -> counts.reserve(Set.of("minecraft", "discord"), 1),
                    () -> counts.reserve(Set.of("discord"), 1));
            int accepted = 0;
            for (var result : pool.invokeAll(calls)) if (result.get()) accepted++;
            assertEquals(1, accepted);
            assertEquals(1, counts.snapshot().get("discord"));
        } finally { pool.shutdownNow(); }
    }

    @Test void failedAndRepeatedDeletionCannotFreeAnotherTicketsSlot() {
        var counts = new TicketCounts();
        var owners = Set.of("minecraft", "discord");
        counts.restore("first", owners);
        counts.restore("second", owners);
        assertTrue(counts.beginClose("first"));
        assertFalse(counts.beginClose("first"));
        counts.closeFailed("first");
        assertFalse(counts.reserve(owners, 2));
        assertTrue(counts.beginClose("first"));
        counts.closed("first");
        counts.closed("first");
        assertEquals(1, counts.snapshot().get("discord"));
        assertFalse(counts.beginClose("first"));
    }

    @Test void failedCreationReleasesItsReservationAndRestoreIsIdempotent() {
        var counts = new TicketCounts();
        var keys = Set.of("discord");
        assertTrue(counts.reserve(keys, 1));
        counts.release(keys);
        assertTrue(counts.reserve(keys, 1));
        counts.opened("channel", keys);
        counts.restore("channel", keys);
        assertEquals(1, counts.snapshot().get("discord"));
    }
}

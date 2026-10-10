package dev.demonz.zdiscord.modules;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class TicketStateTest {
    @Test void concurrentClaimsHaveOneDurableOwner() throws Exception {
        List<String> persisted = new ArrayList<>();
        var state = new TicketState((channel, staff) -> persisted.add(staff));
        state.restore("ticket", 0, null);
        var pool = Executors.newFixedThreadPool(8);
        List<java.util.concurrent.Future<String>> claims = new ArrayList<>();
        try {
            for (int i = 0; i < 100; i++) {
                String staff = "staff-" + i;
                claims.add(pool.submit(() -> state.claim("ticket", staff)));
            }
            String owner = claims.get(0).get(5, TimeUnit.SECONDS);
            for (var claim : claims) assertEquals(owner, claim.get(5, TimeUnit.SECONDS));
            assertEquals(List.of(owner), persisted);
        } finally { pool.shutdownNow(); }
    }

    @Test void restoredClaimsCannotBeReassignedAndDeletedTicketsCannotBeClaimed() {
        List<String> persisted = new ArrayList<>();
        var state = new TicketState((channel, staff) -> persisted.add(staff));
        state.restore("ticket", 0, "first");
        assertEquals("first", state.claim("ticket", "second"));
        assertTrue(persisted.isEmpty());
        state.remove("ticket");
        state.remove("ticket");
        assertEquals(List.of(""), persisted);
        assertNull(state.claim("ticket", "second"));
    }

    @Test void inactivityRespectsNewMessagesOfflineTimeAndDisabledSetting() {
        var state = new TicketState((channel, staff) -> { });
        long now = 100_000_000L;
        state.restore("old", now - 7_200_000, null);
        state.restore("active", now - 7_200_000, null);
        state.touch("active", now - 1_000);
        state.touch("active", now - 7_200_000);
        state.touch("untracked", 0);
        assertEquals(List.of("old"), state.expired(now, 2));
        assertTrue(state.expired(now, 0).isEmpty());
        assertTrue(state.expired(now, -1).isEmpty());
        assertTrue(state.expired(now, Double.NaN).isEmpty());
        state.remove("old");
        assertTrue(state.expired(now, 2).isEmpty());
    }

    @Test void snowflakeActivityRecoversDiscordCreationTime() {
        long timestamp = 1_800_000_000_000L;
        String id = Long.toUnsignedString((timestamp - 1_420_070_400_000L) << 22);
        assertEquals(timestamp, TicketState.createdAt(id));
    }

    @Test void messageArrivingAfterExpiryScanPreventsDeletion() {
        var state = new TicketState((channel, staff) -> { });
        state.restore("ticket", 0, null);
        assertEquals(List.of("ticket"), state.expired(7_200_000, 1));
        state.touch("ticket", 7_200_000);
        assertFalse(state.beginExpiry("ticket", 7_200_000, 1, () -> fail("Must not close an active ticket")));
        assertTrue(state.beginExpiry("ticket", 10_800_000, 1, () -> true));
    }
}

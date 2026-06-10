package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.testsupport.BukkitStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderUtilTest {

    private BukkitStub.State state;

    @BeforeEach
    void setUp() {
        state = BukkitStub.install();
    }

    @AfterEach
    void tearDown() {
        BukkitStub.uninstall();
    }

    @Test
    void resolvesOnlineAndMax() {
        state.withMaxPlayers(0);
        String result = PlaceholderUtil.resolveServer(
                "Online: %online%/%max%");
        assertEquals("Online: 0/0", result);
    }

    @Test
    void onlineAndMaxUseStubbedValues() {
        state.withMaxPlayers(50).withOnlineCount(7);
        String result = PlaceholderUtil.resolveServer("Online: %online%/%max%");
        assertEquals("Online: 7/50", result);
    }

    @Test
    void resolvesTps() {
        state.withTps(20.0, 20.0, 20.0);
        String result = PlaceholderUtil.resolveServer("TPS: %tps%");
        assertTrue(result.startsWith("TPS: 20.0"),
                "Expected TPS to start with 20.0, got: " + result);
    }

    @Test
    void nullReturnsEmpty() {
        assertEquals("", PlaceholderUtil.resolve(null, null));
    }

    @Test
    void nullPlayerLeavesTokensUnresolved() {
        String result = PlaceholderUtil.resolve(
                "name=%name% player=%player%", null);
        assertEquals("name=%name% player=%player%", result);
    }
}

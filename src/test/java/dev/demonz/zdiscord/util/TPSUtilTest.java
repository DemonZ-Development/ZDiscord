package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.testsupport.BukkitStub;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TPSUtilTest {

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
    void returnsThreeElementArray() {
        double[] tps = TPSUtil.getTPS();
        assertNotNull(tps);
        assertEquals(3, tps.length, "getTPS should return 1m/5m/15m triple");
    }

    @Test
    void valuesAreSane() {
        double[] tps = TPSUtil.getTPS();
        for (double v : tps) {
            assertNotNull(Double.valueOf(v));
            assertTrue(v >= 0.0, "TPS should be non-negative");
            assertTrue(v <= 25.0,
                    "TPS should be capped around 20-25 (got " + v + ")");
        }
    }

    @Test
    void reflectsServerStubTps() {
        state.withTps(19.5, 20.0, 20.0);
        double[] tps = TPSUtil.getTPS();
        assertEquals(19.5, tps[0], 0.0001);
    }
}

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

package dev.demonz.zdiscord.util;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TPSUtilTest {

    private ServerMock server;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
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
            assertEquals(true, v >= 0.0, "TPS should be non-negative");
            assertEquals(true, v <= 25.0,
                    "TPS should be capped around 20-25 (got " + v + ")");
        }
    }
}

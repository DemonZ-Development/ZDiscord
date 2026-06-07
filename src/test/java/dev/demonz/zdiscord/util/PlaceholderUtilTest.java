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
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholderUtilTest {

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
    void resolvesOnlineAndMax() {
        String result = PlaceholderUtil.resolveServer(
                "Online: %online%/%max%");
        assertEquals("Online: 0/0", result);
    }

    @Test
    void resolvesTps() {
        String result = PlaceholderUtil.resolveServer("TPS: %tps%");
        assertTrue(result.startsWith("TPS: 20.0"),
                "Expected TPS to start with 20.0, got: " + result);
    }

    @Test
    void nullReturnsEmpty() {
        assertEquals("", PlaceholderUtil.resolve(null, null));
    }

    @Test
    void resolvesPlayerTokens() {
        server.addSimpleWorld("world");
        var player = server.addPlayer();
        String result = PlaceholderUtil.resolve(
                "name=%name% player=%player% uuid=%uuid% world=%world%",
                player);
        assertTrue(result.contains("name=" + player.getName()));
        assertTrue(result.contains("player=" + player.getName()));
        assertTrue(result.contains("world=world"));
    }
}

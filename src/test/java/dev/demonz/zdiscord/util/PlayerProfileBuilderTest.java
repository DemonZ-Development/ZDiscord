/*
 * Copyright 2026 DemonZ Development
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerProfileBuilderTest {

    @Test
    void formatDurationZero() {
        assertEquals("0m", PlayerProfileBuilder.formatDuration(0));
    }

    @Test
    void formatDurationMinutes() {
        assertEquals("5m", PlayerProfileBuilder.formatDuration(300));
    }

    @Test
    void formatDurationHoursAndMinutes() {
        assertEquals("2h 30m", PlayerProfileBuilder.formatDuration(9000));
    }

    @Test
    void formatDurationDaysAndHours() {
        assertEquals("1d 3h", PlayerProfileBuilder.formatDuration(97200));
    }

    @Test
    void formatDateZero() {
        assertEquals("unknown", PlayerProfileBuilder.formatDate(0));
    }

    @Test
    void formatDateNonZero() {
        // 1609459200000 ms = 2021-01-01 00:00:00 UTC
        String result = PlayerProfileBuilder.formatDate(1609459200000L);
        assertEquals("<t:1609459200:R>", result);
    }
}

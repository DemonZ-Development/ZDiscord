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

        String result = PlayerProfileBuilder.formatDate(1609459200000L);
        assertEquals("<t:1609459200:R>", result);
    }
}

package dev.demonz.zdiscord.modules;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IntegrationModuleTest {

    @Test
    void leavesShortMessagesAlone() {
        assertEquals("hello", IntegrationModule.truncate("hello", 10));
    }

    @Test
    void truncatesToDiscordLimitWithoutOverflow() {
        assertEquals("abcd\u2026", IntegrationModule.truncate("abcdefgh", 5));
    }

    @Test
    void preservesNullMessages() {
        assertNull(IntegrationModule.truncate(null, 5));
    }
}

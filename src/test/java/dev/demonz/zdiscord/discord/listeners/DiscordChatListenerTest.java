package dev.demonz.zdiscord.discord.listeners;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiscordChatListenerTest {

    @Test
    void permanentlyDeniesPrivilegeAndArbitraryExecutionCommands() {
        assertTrue(DiscordChatListener.isPermanentlyDenied("stop"));
        assertTrue(DiscordChatListener.isPermanentlyDenied("OP"));
        assertTrue(DiscordChatListener.isPermanentlyDenied("execute"));
        assertTrue(DiscordChatListener.isPermanentlyDenied("luckperms"));
    }

    @Test
    void doesNotDenyOrdinaryCommandsAtTheHardDenyLayer() {
        assertFalse(DiscordChatListener.isPermanentlyDenied("list"));
        assertFalse(DiscordChatListener.isPermanentlyDenied("weather"));
    }
}

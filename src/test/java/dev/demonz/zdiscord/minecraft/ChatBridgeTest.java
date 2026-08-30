package dev.demonz.zdiscord.minecraft;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatBridgeTest {

    @Test
    void neutralizesDiscordMentionsFromMinecraft() {
        assertEquals("hi @\u200Beveryone and @\u200Bhere",
                ChatBridge.preventDiscordMentions("hi @everyone and @here"));
    }
}

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

package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.minecraft.ChatBridge;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Paper-specific chat listener that consumes the modern
 * {@code AsyncChatEvent}. Does NOT cancel the event — the chat
 * message must still be shown to the player. The legacy
 * {@link ChatListener} is simply not registered on Paper, so there
 * is no double-send.
 *
 * <p>Only registered when Paper is detected at startup — see
 * {@code ZDiscord.registerListeners()}.</p>
 */
public class PaperChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ZDiscord plugin;

    public PaperChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        String message = PLAIN.serialize(event.message());
        ChatBridge.forward(plugin, event.getPlayer(), message);
    }
}

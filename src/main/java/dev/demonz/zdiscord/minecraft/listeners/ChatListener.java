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

package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.minecraft.ChatBridge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Forwards Minecraft chat messages to the configured Discord channel.
 *
 * <p>This listener is the universal fallback for Spigot servers and for
 * Paper servers where the modern {@code AsyncChatEvent} was not
 * cancelled. On Paper the higher-priority modern listener is
 * responsible for the actual send; we set {@code ignoreCancelled = true}
 * here so we never double-send.</p>
 */
public class ChatListener implements Listener {

    private final ZDiscord plugin;

    public ChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        ChatBridge.forward(plugin, event.getPlayer(), event.getMessage());
    }
}

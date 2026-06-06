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
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Forwards Minecraft chat messages to the configured Discord channel,
 * using webhooks (when enabled) to display player heads as avatars.
 */
public class ChatListener implements Listener {

    private final ZDiscord plugin;

    public ChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getBotManager().isConnected()) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.hasPermission("zdiscord.chat")) {
            return;
        }

        String message = ColorUtil.stripColor(event.getMessage());

        TextChannel chatChannel = plugin.getBotManager().getTextChannel("channels.chat");
        if (chatChannel == null) {
            return;
        }

        boolean useWebhooks = plugin.getConfigManager().getBoolean("chat.use-webhooks", true);

        if (useWebhooks && plugin.getWebhookManager() != null) {
            String avatarUrl = plugin.getConfigManager()
                    .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
            String resolvedAvatar = HeadUtil.resolve(avatarUrl, player.getUniqueId(), player.getName());
            String webhookName = plugin.getConfigManager()
                    .getString("chat.webhook-name", "%displayname%")
                    .replace("%player%", player.getName())
                    .replace("%displayname%", ColorUtil.stripColor(player.getDisplayName()));

            plugin.getWebhookManager().sendWebhookMessage(chatChannel, webhookName, resolvedAvatar, message);
        } else {
            chatChannel.sendMessage("**" + player.getName() + "** " + message).queue();
        }
    }
}

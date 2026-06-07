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

package dev.demonz.zdiscord.minecraft;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;

/**
 * Shared logic for forwarding a Minecraft chat message to Discord.
 * Used by both the legacy {@code AsyncPlayerChatEvent} listener and
 * the modern Paper {@code AsyncChatEvent} listener so the behaviour
 * stays consistent across platforms.
 */
public final class ChatBridge {

    private ChatBridge() {
    }

    public static void forward(ZDiscord plugin, Player player, String rawMessage) {
        if (plugin == null || player == null || rawMessage == null) {
            return;
        }
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!player.hasPermission("zdiscord.chat")) {
            return;
        }

        String message = ColorUtil.stripColor(rawMessage);
        if (message.trim().isEmpty()) {
            return;
        }

        TextChannel chatChannel = plugin.getBotManager().getTextChannel("channels.chat");
        if (chatChannel == null) {
            plugin.debug("Chat bridge: channels.chat is not configured.");
            return;
        }

        boolean useWebhooks = plugin.getConfigManager().getBoolean("chat.use-webhooks", true);

        if (useWebhooks && plugin.getWebhookManager() != null) {
            String avatarFormat = plugin.getConfigManager()
                    .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
            String resolvedAvatar = HeadUtil.resolve(avatarFormat, player.getUniqueId(), player.getName());

            String nameFormat = plugin.getConfigManager()
                    .getString("chat.webhook-name", "%displayname%");
            String webhookName = nameFormat
                    .replace("%player%", player.getName())
                    .replace("%displayname%", ColorUtil.stripColor(player.getDisplayName()))
                    .replace("%uuid%", player.getUniqueId().toString());

            plugin.getWebhookManager().sendWebhookMessage(chatChannel, webhookName, resolvedAvatar, message);
        } else {
            String plain = "**" + player.getName() + "**: " + message;
            chatChannel.sendMessage(plain).queue(
                    success -> { },
                    error -> plugin.debug("Chat send failed: " + error.getMessage()));
        }
    }
}

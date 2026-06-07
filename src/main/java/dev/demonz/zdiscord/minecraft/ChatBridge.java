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
import net.dv8tion.jda.api.entities.Member;
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
            plugin.debug("Chat bridge: bot is not connected; skipping MC->Discord for "
                    + player.getName());
            return;
        }
        if (!player.hasPermission("zdiscord.chat")) {
            plugin.debug("Chat bridge: " + player.getName()
                    + " lacks zdiscord.chat permission; skipping");
            return;
        }

        String message = ColorUtil.stripColor(rawMessage);
        if (message.trim().isEmpty()) {
            return;
        }

        TextChannel chatChannel = plugin.getBotManager().getTextChannel("channels.chat");
        if (chatChannel == null) {
            plugin.debug("Chat bridge: channels.chat is not configured (or id is wrong).");
            return;
        }

        boolean useWebhooks = plugin.getConfigManager().getBoolean("chat.use-webhooks", true);
        boolean preferLinkedAvatar = plugin.getConfigManager()
                .getBoolean("chat.prefer-linked-avatar", true);
        boolean preferLinkedName = plugin.getConfigManager()
                .getBoolean("chat.prefer-linked-name", true);

        // Resolve a linked Discord identity (if any) so the webhook
        // can show the player's real Discord name and avatar instead
        // of their Minecraft head and username.
        Member linkedMember = resolveLinkedMember(plugin, player);

        if (useWebhooks && plugin.getWebhookManager() != null) {
            String avatarFormat = plugin.getConfigManager()
                    .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
            String resolvedAvatar;
            if (preferLinkedAvatar && linkedMember != null
                    && linkedMember.getUser().getEffectiveAvatarUrl() != null) {
                // Discord CDN URL — needs a size hint for Discord to render it.
                resolvedAvatar = linkedMember.getUser().getEffectiveAvatarUrl()
                        + (linkedMember.getUser().getEffectiveAvatarUrl().contains("?")
                                ? "&size=128"
                                : "?size=128");
            } else {
                resolvedAvatar = HeadUtil.resolve(avatarFormat, player.getUniqueId(), player.getName());
            }

            String nameFormat = plugin.getConfigManager()
                    .getString("chat.webhook-name", "%displayname%");
            String webhookName;
            if (preferLinkedName && linkedMember != null) {
                // Show the linked Discord identity. If the player has a
                // server nickname that differs from their global name,
                // prefer the effective (server) name; otherwise the
                // global username. Tag with the MC name in brackets so
                // the channel can still tell who it is.
                String discordName = linkedMember.getEffectiveName();
                webhookName = nameFormat
                        .replace("%player%", player.getName())
                        .replace("%displayname%", discordName)
                        .replace("%uuid%", player.getUniqueId().toString())
                        .replace("%discord_name%", discordName);
                // The default nameFormat is "%displayname%"; with
                // linked identity we want to fall back to the
                // Discord name so the resolved string is meaningful
                // even when the operator didn't customise it.
                if ("%displayname%".equals(nameFormat)) {
                    webhookName = discordName + " (" + player.getName() + ")";
                }
            } else {
                webhookName = nameFormat
                        .replace("%player%", player.getName())
                        .replace("%displayname%", ColorUtil.stripColor(player.getDisplayName()))
                        .replace("%uuid%", player.getUniqueId().toString());
            }

            plugin.getWebhookManager().sendWebhookMessage(chatChannel, webhookName, resolvedAvatar, message);
        } else {
            // Non-webhook path — also resolve the linked name when
            // configured, so the message body reads "DiscordName (MCName): hi"
            // instead of just "MCName: hi".
            String name;
            if (preferLinkedName && linkedMember != null) {
                name = linkedMember.getEffectiveName() + " (" + player.getName() + ")";
            } else {
                name = player.getName();
            }
            String plain = "**" + name + "**: " + message;
            chatChannel.sendMessage(plain).queue(
                    success -> { },
                    error -> plugin.debug("Chat send failed: " + error.getMessage()));
        }
    }

    private static Member resolveLinkedMember(ZDiscord plugin, Player player) {
        try {
            if (plugin.getLinkModule() == null) {
                return null;
            }
            String discordId = plugin.getLinkModule().getDiscordId(player.getUniqueId());
            if (discordId == null || discordId.isEmpty()) {
                return null;
            }
            var guild = plugin.getBotManager() == null ? null : plugin.getBotManager().getGuild();
            if (guild == null) {
                return null;
            }
            return guild.getMemberById(discordId);
        } catch (Exception e) {
            plugin.debug("Chat bridge: failed to resolve linked member: " + e.getMessage());
            return null;
        }
    }
}

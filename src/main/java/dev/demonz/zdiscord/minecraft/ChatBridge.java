package dev.demonz.zdiscord.minecraft;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;


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




        Member linkedMember = resolveLinkedMember(plugin, player);

        if (useWebhooks && plugin.getWebhookManager() != null) {
            String avatarFormat = plugin.getConfigManager()
                    .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
            String resolvedAvatar;
            if (preferLinkedAvatar && linkedMember != null
                    && linkedMember.getUser().getEffectiveAvatarUrl() != null) {

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





                String discordName = linkedMember.getEffectiveName();
                webhookName = nameFormat
                        .replace("%player%", player.getName())
                        .replace("%displayname%", discordName)
                        .replace("%uuid%", player.getUniqueId().toString())
                        .replace("%discord_name%", discordName);




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

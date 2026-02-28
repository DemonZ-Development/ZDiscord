package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Listens for Minecraft chat events and forwards them to Discord.
 */
public class ChatListener implements Listener {

    private final ZDiscord plugin;

    public ChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!plugin.getBotManager().isConnected())
            return;

        Player player = event.getPlayer();
        if (!player.hasPermission("zdiscord.chat"))
            return;

        String message = event.getMessage();
        // Strip color codes for Discord
        message = message.replaceAll("§[0-9a-fk-or]", "");
        message = message.replaceAll("&[0-9a-fk-or]", "");

        TextChannel chatChannel = plugin.getBotManager().getTextChannel("channels.chat");
        if (chatChannel == null)
            return;

        boolean useWebhooks = plugin.getConfigManager().getBoolean("chat.use-webhooks", true);

        if (useWebhooks && plugin.getWebhookManager() != null) {
            // Send via webhook (player head as avatar)
            String avatarUrl = "https://minotar.net/avatar/" + player.getName() + "/128.png";
            String webhookName = plugin.getConfigManager()
                    .getString("chat.webhook-name", "%displayname%")
                    .replace("%player%", player.getName())
                    .replace("%displayname%", player.getDisplayName().replaceAll("§[0-9a-fk-or]", ""));

            plugin.getWebhookManager().sendWebhookMessage(chatChannel, webhookName, avatarUrl, message);
        } else {
            // Send as bot message
            String finalMessage = "**" + player.getName() + "** » " + message;
            chatChannel.sendMessage(finalMessage).queue();
        }
    }
}

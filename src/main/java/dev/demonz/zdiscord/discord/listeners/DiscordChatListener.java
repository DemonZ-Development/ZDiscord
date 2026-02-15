package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.stream.Collectors;

/**
 * Listens for Discord messages and forwards them to Minecraft.
 */
public class DiscordChatListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public DiscordChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bots and webhooks
        if (event.getAuthor().isBot() || event.isWebhookMessage())
            return;

        String chatChannelId = plugin.getConfigManager().getString("channels.chat");
        String consoleChannelId = plugin.getConfigManager().getString("channels.console");

        // Chat sync: Discord → Minecraft
        if (event.getChannel().getId().equals(chatChannelId)) {
            handleChatMessage(event);
        }

        // Console channel: execute commands
        if (event.getChannel().getId().equals(consoleChannelId)) {
            handleConsoleCommand(event);
        }
    }

    private void handleChatMessage(MessageReceivedEvent event) {
        Member member = event.getMember();
        String name = member != null ? member.getEffectiveName() : event.getAuthor().getName();
        String message = event.getMessage().getContentDisplay();

        // Format for Minecraft
        String format = plugin.getConfigManager().getString("chat.minecraft-format",
                "&9[Discord] &b%name% &8» &f%message%");
        String formatted = format
                .replace("%name%", name)
                .replace("%message%", message);
        formatted = ChatColor.translateAlternateColorCodes('&', formatted);

        // Handle attachments
        if (plugin.getConfigManager().getBoolean("chat.show-attachments", true)) {
            String attachments = event.getMessage().getAttachments().stream()
                    .map(Message.Attachment::getUrl)
                    .collect(Collectors.joining(", "));
            if (!attachments.isEmpty()) {
                String attachText = plugin.getConfigManager().getString("chat.attachment-text",
                        "&e[📎 Attachment] &7(Click to open)");
                formatted += " " + ChatColor.translateAlternateColorCodes('&', attachText);
            }
        }

        // Broadcast to Minecraft
        String finalMessage = formatted;
        plugin.getPlatformAdapter().runSync(() -> Bukkit.broadcastMessage(finalMessage));
    }

    private void handleConsoleCommand(MessageReceivedEvent event) {
        // Check for console prefix
        String prefix = plugin.getConfigManager().getString("misc.console-prefix", "!");
        String content = event.getMessage().getContentRaw();

        if (!content.startsWith(prefix))
            return;

        // Role-based access control
        String requiredRoleId = plugin.getConfigManager().getString("misc.console-role", "");
        if (requiredRoleId.isEmpty()) {
            // No role configured — deny all for safety
            event.getMessage().reply("❌ Console commands are disabled. Set `misc.console-role` in config.yml.").queue();
            return;
        }

        if (event.getMember() == null || event.getMember().getRoles().stream()
                .noneMatch(r -> r.getId().equals(requiredRoleId))) {
            event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🚫")).queue();
            return;
        }

        String command = content.substring(prefix.length()).trim();
        if (command.isEmpty())
            return;

        // Execute command on main thread
        plugin.getPlatformAdapter().runSync(() -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("✅")).queue();
            } catch (Exception e) {
                event.getMessage().addReaction(net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❌")).queue();
                event.getChannel().sendMessage("```\nError: " + e.getMessage() + "\n```").queue();
            }
        });
    }
}

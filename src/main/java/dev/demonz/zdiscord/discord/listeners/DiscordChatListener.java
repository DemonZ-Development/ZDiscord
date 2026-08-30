package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DiscordChatListener extends ListenerAdapter {

    private static final Set<String> NEVER_ALLOWED_CONSOLE_COMMANDS = Set.of(
            "stop", "restart", "reload", "op", "deop", "execute", "function",
            "sudo", "plugman", "pluginmanager", "lp", "luckperms", "pex", "permissions");

    private final ZDiscord plugin;

    public DiscordChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) return;

        String chatChannelId = plugin.getConfigManager().getString("channels.chat");
        String consoleChannelId = plugin.getConfigManager().getString("channels.console");

        if (chatChannelId == null || chatChannelId.isEmpty()) {
            plugin.debug("Discord chat listener fired but channels.chat is not set in config.yml");
            return;
        }

        if (event.getChannel().getId().equals(chatChannelId)) {
            handleChatMessage(event);
        } else if (consoleChannelId != null && event.getChannel().getId().equals(consoleChannelId)) {
            handleConsoleCommand(event);
        } else {
            plugin.debug("Discord message in non-bridge channel #"
                    + event.getChannel().getName() + " (id=" + event.getChannel().getId() + "); ignoring");
        }
    }

    private void handleChatMessage(MessageReceivedEvent event) {
        Member member = event.getMember();
        String name = member != null ? member.getEffectiveName() : event.getAuthor().getName();
        String message = event.getMessage().getContentDisplay();

        String format = plugin.getConfigManager().getString("chat.minecraft-format",
                "&9[Discord] &b%name% &8> &f%message%");
        String formatted = ChatColor.translateAlternateColorCodes('&',
                format.replace("%name%", name).replace("%message%", message));

        if (plugin.getConfigManager().getBoolean("chat.show-replies", true)) {
            String replyLine = buildReplyLine(event.getMessage());
            if (replyLine != null) {
                formatted = replyLine + "\n" + formatted;
            }
        }

        if (plugin.getConfigManager().getBoolean("chat.show-attachments", true)
                && !event.getMessage().getAttachments().isEmpty()) {
            String attachText = plugin.getConfigManager().getString("chat.attachment-text",
                    "&e[Attachment] &7(Click to open)");
            formatted += " " + ChatColor.translateAlternateColorCodes('&', attachText);
        }

        String finalMessage = formatted;
        plugin.getPlatformAdapter().runSync(() -> Bukkit.broadcastMessage(finalMessage));
    }

    private String buildReplyLine(Message message) {
        Message referenced = message.getReferencedMessage();
        if (referenced == null) {
            return null;
        }
        String replyName = referenced.getAuthor().getName();
        String replyText = referenced.getContentDisplay();
        int maxLength = Math.max(10, plugin.getConfigManager()
                .getInt("chat.reply-preview-length", 60));
        if (replyText.length() > maxLength) {
            replyText = replyText.substring(0, maxLength - 3) + "...";
        }

        String replyFormat = plugin.getConfigManager().getString("chat.reply-format",
                "&7┌ Replying to &b%reply_name%&7: &f%reply_message%");
        return ChatColor.translateAlternateColorCodes('&',
                replyFormat.replace("%reply_name%", replyName)
                        .replace("%reply_message%", replyText));
    }

    private void handleConsoleCommand(MessageReceivedEvent event) {
        String prefix = plugin.getConfigManager().getString("misc.console-prefix", "!");
        String content = event.getMessage().getContentRaw();
        if (!content.startsWith(prefix)) return;

        String requiredRoleId = plugin.getConfigManager().getString("misc.console-role", "");
        if (requiredRoleId.isEmpty()) {
            event.getMessage().reply(plugin.getMessageManager().getRaw("console-disabled")).queue();
            return;
        }

        Member member = event.getMember();
        if (member == null
                || member.getUser().isBot()
                || (!member.hasPermission(Permission.ADMINISTRATOR)
                && member.getRoles().stream().noneMatch(r -> r.getId().equals(requiredRoleId)))) {
            event.getMessage().reply(plugin.getMessageManager().getRaw("console-denied")).queue();
            return;
        }

        String command = content.substring(prefix.length()).trim();
        if (command.isEmpty()) return;

        String base = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        int colon = base.indexOf(':');
        if (colon >= 0) {
            base = base.substring(colon + 1);
        }
        if (!isAllowed(base)) {
            event.getMessage().reply("That command is not on the Discord console allowlist.").queue();
            return;
        }

        plugin.getPlatformAdapter().runSync(() -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                event.getMessage().addReaction(Emoji.fromUnicode("\u2705")).queue();
            } catch (Exception e) {
                event.getMessage().reply("Error: " + e.getMessage()).queue();
            }
        });
    }

    private boolean isAllowed(String base) {
        if (isPermanentlyDenied(base)) return false;
        List<String> configured = plugin.getConfigManager().getStringList("misc.console-allowed-commands");
        Set<String> allowed;
        if (configured == null || configured.isEmpty()) {
            allowed = new HashSet<>(Arrays.asList(
                    "say", "msg", "tell", "w", "whisper", "r", "reply",
                    "list", "tps", "seed", "help", "?",
                    "weather", "time", "difficulty", "gamerule",
                    "give", "clear", "effect", "enchant",
                    "teleport", "tp", "summon",
                    "gamemode", "xp", "experience",
                    "whitelist", "ban", "banlist", "pardon",
                    "kick", "kill", "spawnpoint", "setblock", "fill",
                    "title", "advancement", "attribute", "bossbar",
                    "particle", "playsound", "stopsound"));
        } else {
            allowed = new HashSet<>();
            for (String s : configured) {
                allowed.add(s.toLowerCase(Locale.ROOT));
            }
        }
        return allowed.contains(base);
    }

    static boolean isPermanentlyDenied(String command) {
        return command != null && NEVER_ALLOWED_CONSOLE_COMMANDS.contains(
                command.toLowerCase(Locale.ROOT));
    }
}

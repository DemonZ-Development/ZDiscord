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

import java.util.stream.Collectors;

/**
 * Bridges Discord messages to Minecraft and routes console-channel
 * messages to the server command dispatcher.
 */
public class DiscordChatListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public DiscordChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage()) {
            return;
        }

        String chatChannelId = plugin.getConfigManager().getString("channels.chat");
        String consoleChannelId = plugin.getConfigManager().getString("channels.console");

        if (event.getChannel().getId().equals(chatChannelId)) {
            handleChatMessage(event);
        } else if (event.getChannel().getId().equals(consoleChannelId)) {
            handleConsoleCommand(event);
        }
    }

    private void handleChatMessage(MessageReceivedEvent event) {
        Member member = event.getMember();
        String name = member != null ? member.getEffectiveName() : event.getAuthor().getName();
        String message = event.getMessage().getContentDisplay();

        String format = plugin.getConfigManager().getString("chat.minecraft-format",
                "&9[Discord] &b%name% &8> &f%message%");
        String formatted = format
                .replace("%name%", name)
                .replace("%message%", message);
        formatted = ChatColor.translateAlternateColorCodes('&', formatted);

        if (plugin.getConfigManager().getBoolean("chat.show-attachments", true)) {
            String attachments = event.getMessage().getAttachments().stream()
                    .map(Message.Attachment::getUrl)
                    .collect(Collectors.joining(", "));
            if (!attachments.isEmpty()) {
                String attachText = plugin.getConfigManager().getString("chat.attachment-text",
                        "&e[Attachment] &7(Click to open)");
                formatted += " " + ChatColor.translateAlternateColorCodes('&', attachText);
            }
        }

        String finalMessage = formatted;
        plugin.getPlatformAdapter().runSync(() -> Bukkit.broadcastMessage(finalMessage));
    }

    private void handleConsoleCommand(MessageReceivedEvent event) {
        String prefix = plugin.getConfigManager().getString("misc.console-prefix", "!");
        String content = event.getMessage().getContentRaw();

        if (!content.startsWith(prefix)) {
            return;
        }

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
        if (command.isEmpty()) {
            return;
        }

        // Reject commands that would be unsafe to run as console. The console
        // sender has full op-level permissions, so the whitelist is conservative.
        String lc = command.toLowerCase();
        if (lc.startsWith("op ")
                || lc.startsWith("deop ")
                || lc.startsWith("stop")
                || lc.startsWith("restart")
                || lc.startsWith("reload confirm")
                || lc.startsWith("whitelist remove ")) {
            event.getMessage().reply("That command is not allowed via Discord console.")
                    .queue();
            return;
        }

        String finalCommand = command;
        plugin.getPlatformAdapter().runSync(() -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                event.getMessage().addReaction(Emoji.fromUnicode("\u2705")).queue();
            } catch (Exception e) {
                event.getMessage().reply("Error: " + e.getMessage()).queue();
            }
        });
    }
}

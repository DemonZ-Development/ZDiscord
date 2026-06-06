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

package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main admin command: {@code /zdiscord &lt;subcommand&gt;}.
 *
 * <p>Does not dump any secret values to its diagnostics output.</p>
 */
public class ZDiscordCommand implements CommandExecutor, TabCompleter {

    private final ZDiscord plugin;

    public ZDiscordCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "reload":
                handleReload(sender);
                break;
            case "status":
                handleStatus(sender);
                break;
            case "link":
                handleLink(sender);
                break;
            case "embed":
                handleEmbed(sender, args);
                break;
            case "ticket":
                handleTicket(sender, args);
                break;
            case "lockdown":
                handleLockdown(sender);
                break;
            case "dump":
                handleDump(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("ZDiscord commands:");
        sender.sendMessage("  /zdiscord reload");
        sender.sendMessage("  /zdiscord status");
        sender.sendMessage("  /zdiscord link");
        sender.sendMessage("  /zdiscord embed <title> <description>");
        sender.sendMessage("  /zdiscord ticket <subject>");
        sender.sendMessage("  /zdiscord lockdown");
        sender.sendMessage("  /zdiscord dump");
        sender.sendMessage("");
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        plugin.reload();
        sender.sendMessage(plugin.getMessageManager().get("reload-success"));
    }

    private void handleStatus(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }

        var botManager = plugin.getBotManager();
        boolean connected = botManager != null && botManager.isConnected();
        String statusText = connected ? "Online" : "Offline";

        sender.sendMessage("");
        sender.sendMessage("ZDiscord status");
        sender.sendMessage("  Discord bot: " + statusText);
        if (connected) {
            var jda = botManager.getJda();
            if (jda != null) {
                long ping = jda.getGatewayPing();
                String pingLabel = ping < 100 ? "good" : ping < 250 ? "elevated" : "high";
                sender.sendMessage("  Bot name: " + jda.getSelfUser().getName());
                sender.sendMessage("  Gateway ping: " + ping + "ms (" + pingLabel + ")");
                sender.sendMessage("  Guilds: " + jda.getGuilds().size());
            }
        }
        sender.sendMessage("  Platform: " + plugin.getPlatformAdapter().getPlatformName());
        sender.sendMessage("  Version: v" + plugin.getDescription().getVersion());
        sender.sendMessage("");
    }

    private void handleLink(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().get("player-only"));
            return;
        }
        if (!sender.hasPermission("zdiscord.link")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (plugin.getLinkModule() == null) {
            sender.sendMessage("Account linking is disabled in config.yml.");
            return;
        }

        Player player = (Player) sender;
        String code = plugin.getLinkModule().generateCode(player);
        if (code == null) {
            return;
        }
        sender.sendMessage(plugin.getMessageManager().get(
                "link-code-generated", "%code%", code));
    }

    private void handleEmbed(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zdiscord.embed")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /zdiscord embed <title> <description>");
            return;
        }
        String title = args[1];
        String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getEmbedBuilderModule().createAndSend(sender, title, description);
    }

    private void handleTicket(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().get("player-only"));
            return;
        }
        if (!sender.hasPermission("zdiscord.ticket")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (plugin.getTicketModule() == null) {
            sender.sendMessage("The ticket system is disabled in config.yml.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("Usage: /zdiscord ticket <subject>");
            return;
        }
        String subject = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getTicketModule().createTicketFromMC((Player) sender, subject);
    }

    private void handleLockdown(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (plugin.getAntiRaidModule() == null) {
            sender.sendMessage("Anti-raid is disabled in config.yml.");
            return;
        }
        plugin.getAntiRaidModule().toggleLockdown(sender);
    }

    private void handleDump(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }

        File dumpFile = new File(plugin.getDataFolder(),
                "dump-" + System.currentTimeMillis() + ".txt");
        try (PrintWriter out = new PrintWriter(new FileWriter(dumpFile))) {
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println("ZDiscord support dump");
            out.println("Generated: " + timestamp);
            out.println();

            out.println("Server: " + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
            out.println("Platform: " + plugin.getPlatformAdapter().getPlatformName());
            out.println("ZDiscord: v" + plugin.getDescription().getVersion());
            out.println("Java: " + System.getProperty("java.version"));
            out.println("OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            out.println("Online players: " + plugin.getServer().getOnlinePlayers().size());
            out.println();

            out.println("Discord bot");
            boolean connected = plugin.getBotManager() != null && plugin.getBotManager().isConnected();
            out.println("  Connected: " + connected);
            if (connected) {
                var jda = plugin.getBotManager().getJda();
                if (jda != null) {
                    out.println("  Bot name: " + jda.getSelfUser().getName());
                    out.println("  Gateway ping: " + jda.getGatewayPing() + "ms");
                    out.println("  Guilds: " + jda.getGuilds().size());
                }
            }
            out.println();

            out.println("Modules");
            out.println("  Status:       " + (plugin.getStatusModule() != null ? "on" : "off"));
            out.println("  Leaderboard:  " + (plugin.getLeaderboardModule() != null ? "on" : "off"));
            out.println("  Tickets:      " + (plugin.getTicketModule() != null ? "on" : "off"));
            out.println("  Linking:      " + (plugin.getLinkModule() != null ? "on" : "off"));
            out.println("  Anti-raid:    " + (plugin.getAntiRaidModule() != null ? "on" : "off"));
            out.println("  Performance:  " + (plugin.getPerformanceModule() != null ? "on" : "off"));
            out.println("  Cmd logger:   " + (plugin.getCommandLoggerModule() != null ? "on" : "off"));
            out.println("  Staff chat:   " + (plugin.getStaffChatModule() != null ? "on" : "off"));
            out.println("  Voice status: " + (plugin.getVoiceStatusModule() != null ? "on" : "off"));
            out.println();

            out.println("Config");
            out.println("  Guild ID: " + plugin.getConfigManager().getString("bot.guild-id", "not set"));
            out.println("  Chat channel: " + plugin.getConfigManager().getString("channels.chat", "not set"));
            out.println("  Webhooks: " + plugin.getConfigManager().getBoolean("chat.use-webhooks", true));
            out.println("  Link required: " + plugin.getConfigManager().getBoolean("linking.required", false));
            out.println("  Config version: " + plugin.getConfigManager().getInt("config-version", 0));
            out.println();

            sender.sendMessage("Dump saved to " + dumpFile.getName()
                    + ". Share this file with support for troubleshooting.");
        } catch (Exception e) {
            sender.sendMessage("Failed to create dump: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList(
                    "reload", "status", "link", "embed", "ticket", "lockdown", "dump"));
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

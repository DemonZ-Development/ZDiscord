package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.SkinUtil;
import dev.demonz.zdiscord.util.UpdateChecker;
import dev.demonz.zdiscord.util.ZLogger;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ZDiscordCommand implements CommandExecutor, TabCompleter {

    private final ZDiscord plugin;
    private final Set<UUID> dismissedUpdates = ConcurrentHashMap.newKeySet();

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
            case "reload" -> handleReload(sender);
            case "status" -> handleStatus(sender);
            case "link" -> handleLink(sender);
            case "embed" -> handleEmbed(sender, args);
            case "ticket" -> handleTicket(sender, args);
            case "panel" -> handlePanel(sender);
            case "lockdown" -> handleLockdown(sender);
            case "update" -> handleUpdate(sender, args);
            case "dump" -> handleDump(sender);
            case "diagnostics" -> handleDiagnostics(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("");
        sender.sendMessage("ZDiscord commands:");
        sender.sendMessage("  /zdiscord reload");
        sender.sendMessage("  /zdiscord status");
        sender.sendMessage("  /zdiscord diagnostics");
        sender.sendMessage("  /zdiscord link");
        sender.sendMessage("  /zdiscord embed <title> <description>");
        sender.sendMessage("  /zdiscord ticket <subject>");
        sender.sendMessage("  /zdiscord panel");
        sender.sendMessage("  /zdiscord lockdown");
        sender.sendMessage("  /zdiscord update [check|dismiss]");
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

        sender.sendMessage("");
        sender.sendMessage("ZDiscord status");
        sender.sendMessage("  Discord bot: " + (connected ? "Online" : "Offline"));
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
        if (!(sender instanceof Player player)) {
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

        String code = plugin.getLinkModule().generateCode(player);
        if (code == null) return;
        sender.sendMessage(plugin.getMessageManager().get("link-code-generated", "%code%", code));
    }

    private void handleEmbed(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zdiscord.embed")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (plugin.getEmbedBuilderModule() == null) {
            sender.sendMessage("The Discord bot is not connected.");
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("Usage: /zdiscord embed <title> <description>");
            return;
        }
        plugin.getEmbedBuilderModule().createAndSend(sender,
                args[1], String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
    }

    private void handleTicket(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
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
        plugin.getTicketModule().createTicketFromMC(player,
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    }

    private void handlePanel(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (plugin.getTicketModule() == null) {
            sender.sendMessage("The ticket system is disabled in config.yml.");
            return;
        }
        if (plugin.getBotManager() == null || !plugin.getBotManager().isConnected()) {
            sender.sendMessage("The Discord bot is not connected.");
            return;
        }

        String panelChannelId = plugin.getConfigManager().getString("tickets.panel-channel", "");
        if (panelChannelId.isEmpty()) {
            panelChannelId = plugin.getConfigManager().getString("channels.ticket-category", "");
        }
        if (panelChannelId.isEmpty()) {
            sender.sendMessage("Set tickets.panel-channel in config.yml first, "
                    + "or run /setup in Discord to configure tickets.");
            return;
        }

        final String targetChannelId = panelChannelId;
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                var channel = plugin.getBotManager().getJda().getTextChannelById(targetChannelId);
                if (channel == null) {
                    reply(sender, "Channel ID '" + targetChannelId + "' was not found.");
                    return;
                }
                plugin.getTicketModule().postPanel(channel);
                reply(sender, "Ticket panel posted in <#" + targetChannelId + ">.");
            } catch (Exception e) {
                reply(sender, "Failed to post panel: " + e.getMessage());
            }
        });
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

    private void handleUpdate(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }

        if (args.length >= 2) {
            switch (args[1].toLowerCase()) {
                case "dismiss" -> {
                    if (sender instanceof Player player) {
                        dismissedUpdates.add(player.getUniqueId());
                    }
                    sender.sendMessage(plugin.getMessageManager().getRaw("prefix")
                            + "\u00a77Update notification dismissed for this session.");
                    return;
                }
                case "check" -> {
                    runManualUpdateCheck(sender);
                    return;
                }
            }
        }

        sender.sendMessage("Current: v" + plugin.getDescription().getVersion());
        if (plugin.getConfigManager().getBoolean("misc.update-checker", true)) {
            sender.sendMessage("Update checker: enabled. Use /zdiscord update check to query now.");
        } else {
            sender.sendMessage("Update checker: disabled in config.yml.");
        }
    }

    private void runManualUpdateCheck(CommandSender sender) {
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                String latest = UpdateChecker.fetchLatestSync();
                if (latest == null) {
                    reply(sender, "Could not reach the Modrinth API right now.");
                } else if (UpdateChecker.isNewer(latest, plugin.getDescription().getVersion())) {
                    reply(sender, "\u00a7aLatest version: v" + latest
                            + " (you are running v" + plugin.getDescription().getVersion() + ")");
                } else {
                    reply(sender, "\u00a7aYou are running the latest version (v"
                            + plugin.getDescription().getVersion() + ").");
                }
            } catch (Exception e) {
                reply(sender, "Update check failed: " + e.getMessage());
            }
        });
    }

    public boolean isDismissed(UUID playerId) {
        return dismissedUpdates.contains(playerId);
    }

    public void clearDismissed() {
        dismissedUpdates.clear();
    }

    private void reply(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            plugin.getPlatformAdapter().runForEntity(player, () -> player.sendMessage(message));
        } else {
            plugin.getPlatformAdapter().runSync(() -> sender.sendMessage(message));
        }
    }

    private void handleDiagnostics(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }

        sender.sendMessage("");
        sender.sendMessage("\u00a7bZDiscord diagnostics\u00a77 (read-only)");

        var storage = plugin.getStorageManager();
        String storageLine = "  Storage: " + storage.getTypeName();
        int pending = storage.pendingWriteCount();
        if (pending > 0) {
            storageLine += " \u00a7e(" + pending + " writes not yet persisted)";
        } else if (pending == 0) {
            storageLine += " \u00a7a(all writes flushed)";
        }
        sender.sendMessage(storageLine);

        boolean connected = plugin.getBotManager() != null && plugin.getBotManager().isConnected();
        if (connected) {
            long ping = plugin.getBotManager().getJda().getGatewayPing();
            sender.sendMessage("  Discord: connected, gateway ping " + ping + "ms");
            if (plugin.getWebhookManager() != null) {
                sender.sendMessage("  Webhooks active: "
                        + plugin.getWebhookManager().getWebhookCount());
            }
        } else {
            sender.sendMessage("  Discord: \u00a7cnot connected");
        }

        sender.sendMessage("  SkinsRestorer: "
                + (SkinUtil.isAvailable() ? "\u00a7ahooked" : "\u00a77not installed"));
        if (SkinUtil.isGeyserPresent()) {
            int bedrock = countBedrockPlayers();
            sender.sendMessage("  Geyser/Floodgate: \u00a7adetected\u00a77, "
                    + bedrock + " Bedrock player(s) online right now");
        } else {
            sender.sendMessage("  Geyser/Floodgate: not installed");
        }

        sender.sendMessage("  Logging: " + (ZLogger.isDebugMode()
                ? "\u00a7eDEBUG (logging.debug: true)" : "normal (logging.level)"));
        sender.sendMessage("  Modules: status=" + onOff(plugin.getStatusModule() != null)
                + " live-stats=" + onOff(plugin.getLiveStatsModule() != null)
                + " leaderboard=" + onOff(plugin.getLeaderboardModule() != null)
                + " tickets=" + onOff(plugin.getTicketModule() != null)
                + " link=" + onOff(plugin.getLinkModule() != null)
                + " anti-raid=" + onOff(plugin.getAntiRaidModule() != null));
        sender.sendMessage("");
    }

    private int countBedrockPlayers() {
        try {
            return plugin.getPlatformAdapter().supplySync(() -> {
                int n = 0;
                for (var p : plugin.getServer().getOnlinePlayers()) {
                    if (SkinUtil.isBedrockUuid(p.getUniqueId())) n++;
                }
                return n;
            });
        } catch (Exception e) {
            return -1;
        }
    }

    private String onOff(boolean b) {
        return b ? "on" : "off";
    }

    private void handleDump(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }

        File dumpFile = new File(plugin.getDataFolder(), "dump-" + System.currentTimeMillis() + ".txt");
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
            List<String> subs = Arrays.asList("reload", "status", "diagnostics", "link",
                    "embed", "ticket", "panel", "lockdown", "update", "dump");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "update".equalsIgnoreCase(args[0])) {
            return Arrays.asList("check", "dismiss").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }
}

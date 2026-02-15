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
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main admin command: /zdiscord [reload|status|link|embed]
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
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("  §b§l⚡ §3§lZDiscord §8— §7Command Reference");
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("");
        sender.sendMessage("  §b❯ §f/zd reload     §8· §7Reload all configs");
        sender.sendMessage("  §b❯ §f/zd status     §8· §7Bot connection info");
        sender.sendMessage("  §b❯ §f/zd link       §8· §7Link Discord account");
        sender.sendMessage("  §b❯ §f/zd embed      §8· §7Create custom embed");
        sender.sendMessage("  §b❯ §f/zd ticket     §8· §7Open support ticket");
        sender.sendMessage("  §b❯ §f/zd lockdown   §8· §7Toggle raid lockdown");
        sender.sendMessage("  §b❯ §f/zd dump       §8· §7Generate support log");
        sender.sendMessage("");
        sender.sendMessage("§8§m                                              ");
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

        boolean connected = plugin.getBotManager().isConnected();
        String statusIcon = connected ? "§a§l⬤" : "§c§l⬤";
        String statusText = connected ? "§a§lOnline" : "§c§lOffline";

        sender.sendMessage("");
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("  §b§l⚡ §3§lZDiscord §8— §7System Status");
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("");
        sender.sendMessage("  " + statusIcon + " §7Discord Bot    " + statusText);
        if (connected) {
            String botName = plugin.getBotManager().getJda().getSelfUser().getName();
            long ping = plugin.getBotManager().getJda().getGatewayPing();
            int guilds = plugin.getBotManager().getJda().getGuilds().size();

            sender.sendMessage("");
            sender.sendMessage("  §3▸ §7Bot Name     §f" + botName);
            sender.sendMessage(
                    "  §3▸ §7Latency      §f" + ping + "ms" + (ping < 100 ? " §a●" : ping < 250 ? " §e●" : " §c●"));
            sender.sendMessage("  §3▸ §7Guilds       §f" + guilds);
        }
        sender.sendMessage("  §3▸ §7Platform     §f" + plugin.getPlatformAdapter().getPlatformName());
        sender.sendMessage("  §3▸ §7Version      §fv" + plugin.getDescription().getVersion());
        sender.sendMessage("");
        sender.sendMessage("§8§m                                              ");
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
            sender.sendMessage("§cAccount linking is disabled.");
            return;
        }

        Player player = (Player) sender;
        String code = plugin.getLinkModule().generateCode(player);
        sender.sendMessage(plugin.getMessageManager().get("link-code-generated", "%code%", code));
    }

    private void handleEmbed(CommandSender sender, String[] args) {
        if (!sender.hasPermission("zdiscord.embed")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage("§cUsage: /zdiscord embed <title> <description...>");
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
            sender.sendMessage("§cTicket system is disabled.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /zdiscord ticket <subject...>");
            return;
        }

        String subject = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Player player = (Player) sender;
        plugin.getTicketModule().createTicketFromMC(player, subject);
    }

    private void handleLockdown(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }
        if (plugin.getAntiRaidModule() == null) {
            sender.sendMessage("§cAnti-raid module is disabled.");
            return;
        }
        plugin.getAntiRaidModule().toggleLockdown(sender);
    }

    private void handleDump(CommandSender sender) {
        if (!sender.hasPermission("zdiscord.admin")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return;
        }

        try {
            File dumpFile = new File(plugin.getDataFolder(), "dump-" + System.currentTimeMillis() + ".txt");
            PrintWriter out = new PrintWriter(new FileWriter(dumpFile));

            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            out.println("═══════════════════════════════════════");
            out.println("  ZDiscord Support Dump");
            out.println("  Generated: " + timestamp);
            out.println("═══════════════════════════════════════");
            out.println();

            // Server info
            out.println("▸ Server: " + plugin.getServer().getName() + " " + plugin.getServer().getVersion());
            out.println("▸ Platform: " + plugin.getPlatformAdapter().getPlatformName());
            out.println("▸ ZDiscord: v" + plugin.getDescription().getVersion());
            out.println("▸ Java: " + System.getProperty("java.version"));
            out.println("▸ OS: " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
            out.println("▸ Online Players: " + plugin.getServer().getOnlinePlayers().size());
            out.println();

            // Discord bot info
            out.println("── Discord Bot ──");
            boolean connected = plugin.getBotManager() != null && plugin.getBotManager().isConnected();
            out.println("▸ Connected: " + connected);
            if (connected) {
                out.println("▸ Bot User: " + plugin.getBotManager().getJda().getSelfUser().getName());
                out.println("▸ Gateway Ping: " + plugin.getBotManager().getJda().getGatewayPing() + "ms");
                out.println("▸ Guilds: " + plugin.getBotManager().getJda().getGuilds().size());
            }
            out.println();

            // Module status
            out.println("── Modules ──");
            out.println("▸ Status:       " + (plugin.getStatusModule() != null ? "ON" : "OFF"));
            out.println("▸ Leaderboard:  " + (plugin.getLeaderboardModule() != null ? "ON" : "OFF"));
            out.println("▸ Tickets:      " + (plugin.getTicketModule() != null ? "ON" : "OFF"));
            out.println("▸ Linking:      " + (plugin.getLinkModule() != null ? "ON" : "OFF"));
            out.println("▸ Anti-Raid:    " + (plugin.getAntiRaidModule() != null ? "ON" : "OFF"));
            out.println("▸ Performance:  " + (plugin.getPerformanceModule() != null ? "ON" : "OFF"));
            out.println("▸ Cmd Logger:   " + (plugin.getCommandLoggerModule() != null ? "ON" : "OFF"));
            out.println("▸ Staff Chat:   " + (plugin.getStaffChatModule() != null ? "ON" : "OFF"));
            out.println("▸ Voice Status: " + (plugin.getVoiceStatusModule() != null ? "ON" : "OFF"));
            out.println();

            // Config overview (safe — no tokens)
            out.println("── Config ──");
            out.println("▸ Guild ID: " + plugin.getConfigManager().getString("bot.guild-id", "NOT SET"));
            out.println("▸ Chat Channel: " + plugin.getConfigManager().getString("channels.chat", "NOT SET"));
            out.println("▸ Webhooks: " + plugin.getConfigManager().getBoolean("chat.use-webhooks", true));
            out.println("▸ Link Required: " + plugin.getConfigManager().getBoolean("linking.required", false));
            out.println("▸ Config Version: " + plugin.getConfigManager().getConfig().getInt("config-version", 0));
            out.println();

            out.println("── End of Dump ──");
            out.close();

            sender.sendMessage("§b§l⚡ §aDump saved to §f" + dumpFile.getName());
            sender.sendMessage("§7Share this file with support for troubleshooting.");
        } catch (Exception e) {
            sender.sendMessage("§cFailed to create dump: " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(
                    Arrays.asList("reload", "status", "link", "embed", "ticket", "lockdown", "dump"));
            return subs.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}

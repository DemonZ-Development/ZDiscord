package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.EmbedUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.*;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Manages Discord slash commands for server interaction.
 */
public class SlashCommandManager extends ListenerAdapter {

    private final ZDiscord plugin;

    public SlashCommandManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    /**
     * Register all slash commands with the Discord guild.
     */
    public void registerCommands() {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            plugin.getLogger().warning("Could not register slash commands: Guild not found.");
            return;
        }

        guild.updateCommands().addCommands(
                Commands.slash("status", "View the Minecraft server status"),
                Commands.slash("players", "View online players"),
                Commands.slash("tps", "View server performance (TPS/MSPT)"),
                Commands.slash("link", "Link your Discord account to Minecraft")
                        .addOption(OptionType.STRING, "code", "Your link code from /zdiscord link", true),
                Commands.slash("ticket", "Create a support ticket")
                        .addOption(OptionType.STRING, "subject", "Ticket subject", true),
                Commands.slash("leaderboard", "View player leaderboards")
                        .addOption(OptionType.STRING, "stat", "Stat to view (kills, deaths, playtime)", true),
                Commands.slash("setup", "Open the interactive ZDiscord setup wizard")
                        .addOption(OptionType.STRING, "module",
                                "Quick setup: module name (chat, status, events, console, tickets, etc.)",
                                false, true)
                        .addOption(OptionType.CHANNEL, "channel", "Quick setup: target channel", false))
                .queue(
                        success -> plugin.getLogger().info("Registered " + success.size() + " slash commands!"),
                        error -> plugin.getLogger()
                                .warning("Failed to register slash commands: " + error.getMessage()));

        // Register listeners
        plugin.getBotManager().getJda().addEventListener(this);
        plugin.getBotManager().getJda().addEventListener(new SetupCommand(plugin));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "status":
                handleStatus(event);
                break;
            case "players":
                handlePlayers(event);
                break;
            case "tps":
                handleTps(event);
                break;
            case "link":
                handleLink(event);
                break;
            case "ticket":
                handleTicket(event);
                break;
            case "leaderboard":
                handleLeaderboard(event);
                break;
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double[] tps = dev.demonz.zdiscord.util.TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;

        String serverIp = plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🎮 Server Status")
                .setColor(Color.decode("#5865F2"))
                .addField("📊 Status", "🟢 Online", true)
                .addField("👥 Players", online + "/" + max, true)
                .addField("⚡ TPS", String.format("%.1f", tps[0]), true)
                .addField("💾 Memory", usedMb + "MB / " + maxMb + "MB", true)
                .addField("🌐 Server IP", "`" + serverIp + "`", false)
                .setFooter("ZDiscord • Server Status")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handlePlayers(SlashCommandInteractionEvent event) {
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            event.reply("❌ No players are currently online.").setEphemeral(true).queue();
            return;
        }

        String playerList = players.stream()
                .map(Player::getName)
                .sorted()
                .collect(Collectors.joining("\n• ", "• ", ""));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👥 Online Players (" + players.size() + "/" + Bukkit.getMaxPlayers() + ")")
                .setDescription(playerList)
                .setColor(Color.decode("#2ECC71"))
                .setFooter("ZDiscord • Player List")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleTps(SlashCommandInteractionEvent event) {
        double[] tps = dev.demonz.zdiscord.util.TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        int memPercent = (int) ((usedMb * 100.0) / maxMb);

        String tpsEmoji = tps[0] >= 18 ? "🟢" : (tps[0] >= 15 ? "🟡" : "🔴");
        String memEmoji = memPercent < 70 ? "🟢" : (memPercent < 85 ? "🟡" : "🔴");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Server Performance")
                .setColor(tps[0] >= 18 ? Color.decode("#2ECC71")
                        : (tps[0] >= 15 ? Color.decode("#F1C40F") : Color.decode("#E74C3C")))
                .addField(tpsEmoji + " TPS (1m)", String.format("%.2f", tps[0]), true)
                .addField("⏱ TPS (5m)", String.format("%.2f", tps[1]), true)
                .addField("📈 TPS (15m)", String.format("%.2f", tps[2]), true)
                .addField(memEmoji + " Memory", usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)", true)
                .addField("🧵 Threads", String.valueOf(Thread.activeCount()), true)
                .setFooter("ZDiscord • Performance")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        if (plugin.getLinkModule() == null) {
            event.reply("❌ Account linking is disabled.").setEphemeral(true).queue();
            return;
        }

        String code = event.getOption("code").getAsString();
        boolean success = plugin.getLinkModule().processLink(event.getUser().getId(), event.getUser().getName(), code);

        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🔗 Account Linked!")
                    .setDescription("Your Discord account has been successfully linked!")
                    .setColor(Color.decode("#2ECC71"))
                    .setTimestamp(Instant.now());
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("❌ Invalid or expired link code. Use `/zdiscord link` in Minecraft first.").setEphemeral(true)
                    .queue();
        }
    }

    private void handleTicket(SlashCommandInteractionEvent event) {
        if (plugin.getTicketModule() == null) {
            event.reply("❌ Ticket system is disabled.").setEphemeral(true).queue();
            return;
        }

        String subject = event.getOption("subject").getAsString();
        plugin.getTicketModule().createTicket(event.getUser(), subject, event);
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        if (plugin.getLeaderboardModule() == null) {
            event.reply("❌ Leaderboard system is disabled.").setEphemeral(true).queue();
            return;
        }

        String stat = event.getOption("stat").getAsString();
        plugin.getLeaderboardModule().sendLeaderboard(event, stat);
    }
}

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

package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.PlaceholderUtil;
import dev.demonz.zdiscord.util.StatusEmbedBuilder;
import dev.demonz.zdiscord.util.TPSUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Registers the Discord slash commands and dispatches them to the
 * appropriate handlers / modules.
 */
public class SlashCommandManager extends ListenerAdapter {

    private final ZDiscord plugin;

    public SlashCommandManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            plugin.getLogger().warning("Could not register slash commands: guild not found.");
            return;
        }

        guild.updateCommands().addCommands(
                Commands.slash("status", "View the Minecraft server status"),
                Commands.slash("players", "View online players"),
                Commands.slash("tps", "View server performance (TPS and memory)"),
                Commands.slash("link", "Link your Discord account to Minecraft")
                        .addOption(OptionType.STRING, "code",
                                "Your link code from /zdiscord link", true),
                Commands.slash("ticket", "Create a support ticket")
                        .addOption(OptionType.STRING, "subject", "Ticket subject", true),
                Commands.slash("panel", "(Re)post the ticket panel in this channel"),
                Commands.slash("leaderboard", "View player leaderboards")
                        .addOption(OptionType.STRING, "stat",
                                "Stat to view (kills, deaths, playtime)", true),
                Commands.slash("setup", "Open the ZDiscord setup wizard")
                        .addOption(OptionType.STRING, "module",
                                "Quick setup: module name (chat, status, events, etc.)",
                                false, true)
                        .addOption(OptionType.CHANNEL, "channel",
                                "Quick setup: target channel", false))
                .queue(
                        success -> plugin.getLogger().info("Registered "
                                + success.size() + " slash commands."),
                        error -> plugin.getLogger().warning("Failed to register slash commands: "
                                + error.getMessage()));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        // "setup" is handled by the dedicated SetupCommand listener to keep
        // the wizard's interaction tree (selects, buttons) in one place.
        if ("setup".equals(event.getName())) {
            return;
        }
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
            case "panel":
                handlePanel(event);
                break;
            case "leaderboard":
                handleLeaderboard(event);
                break;
            default:
                break;
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        String title = plugin.getMessageManager().getRaw("slash-status-title");
        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                plugin.getBotManager()::getGuild,
                title,
                plugin.getConfigManager().getString("status.embed.color", "#5865F2"),
                plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com"),
                plugin.getConfigManager().getInt("status.update-interval", 30),
                plugin.getConfigManager().getBoolean("status.embed.show-players", true),
                plugin.getConfigManager().getBoolean("status.embed.show-tps", true),
                plugin.getConfigManager().getBoolean("status.embed.show-memory", true),
                plugin.getConfigManager().getDouble("performance.tps-warning", 18.0),
                plugin.getConfigManager().getDouble("performance.tps-critical", 15.0));

        event.replyEmbeds(StatusEmbedBuilder.build(ctx)).queue();
    }

    private void handlePlayers(SlashCommandInteractionEvent event) {
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            event.reply("No players are currently online.").setEphemeral(true).queue();
            return;
        }

        String playerList = players.stream()
                .map(Player::getName)
                .sorted()
                .collect(Collectors.joining("\n", "", ""));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getMessageManager().getRaw("slash-players-title")
                        + " (" + players.size() + "/" + Bukkit.getMaxPlayers() + ")")
                .setDescription(playerList)
                .setColor(ColorUtil.parseHex("#2ECC71"))
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleTps(SlashCommandInteractionEvent event) {
        double[] tps = TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        int memPercent = maxMb > 0 ? (int) ((usedMb * 100.0) / maxMb) : 0;

        double tpsWarning = plugin.getConfigManager().getDouble("performance.tps-warning", 18.0);
        double tpsCritical = plugin.getConfigManager().getDouble("performance.tps-critical", 15.0);
        int memWarning = plugin.getConfigManager().getInt("performance.memory-warning", 80);

        int color;
        if (tps[0] >= tpsWarning && memPercent < memWarning) {
            color = 0x2ECC71;
        } else if (tps[0] >= tpsCritical && memPercent < 90) {
            color = 0xF1C40F;
        } else {
            color = 0xE74C3C;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getMessageManager().getRaw("slash-tps-title"))
                .setColor(color)
                .addField("TPS (1m / 5m / 15m)",
                        String.format("`%.2f` / `%.2f` / `%.2f`", tps[0], tps[1], tps[2]), false)
                .addField("Memory",
                        usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)", true)
                .addField("Threads", String.valueOf(Thread.activeCount()), true)
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        if (plugin.getLinkModule() == null) {
            event.reply("Account linking is disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        String code = event.getOption("code").getAsString();
        boolean success = plugin.getLinkModule().processLink(
                event.getUser().getId(), event.getUser().getName(), code);
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getMessageManager().getRaw("slash-link-title"))
                    .setDescription("Your Discord account has been linked to your Minecraft account.")
                    .setColor(ColorUtil.parseHex("#2ECC71"))
                    .setTimestamp(Instant.now());
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("Invalid or expired link code. Run `/zdiscord link` in Minecraft first.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleTicket(SlashCommandInteractionEvent event) {
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        String subject = event.getOption("subject").getAsString();
        plugin.getTicketModule().createTicket(event.getUser(), subject, event);
    }

    private void handlePanel(SlashCommandInteractionEvent event) {
        if (event.getMember() == null
                || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("You need **Administrator** permission to post the panel.")
                    .setEphemeral(true).queue();
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                event.getChannel().asTextChannel();
        plugin.getTicketModule().postPanel(channel);
        event.reply("Ticket panel posted in " + channel.getAsMention() + ".")
                .setEphemeral(true).queue();
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        if (plugin.getLeaderboardModule() == null) {
            event.reply("Leaderboards are disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        String stat = event.getOption("stat").getAsString();
        plugin.getLeaderboardModule().sendLeaderboard(event, stat);
    }
}

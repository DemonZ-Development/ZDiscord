package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.*;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Auto-updating server status embed module.
 * Posts and continuously edits a single status embed in a configured channel.
 */
public class StatusModule {

    private final ZDiscord plugin;
    private String statusMessageId;

    public StatusModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        int interval = plugin.getConfigManager().getInt("status.update-interval", 30);
        long ticks = interval * 20L;

        // Load saved message ID so we can edit it instead of creating new ones
        statusMessageId = plugin.getConfigManager().getString("channels.status-message", null);

        plugin.getPlatformAdapter().runAsyncTimer(this::updateStatus, 100L, ticks);
    }

    private void updateStatus() {
        String channelId = plugin.getConfigManager().getString("channels.status", "");
        if (channelId == null || channelId.isEmpty() || channelId.startsWith("YOUR_"))
            return;

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.status");
        if (channel == null)
            return;

        EmbedBuilder embed = buildStatusEmbed();

        if (statusMessageId != null && !statusMessageId.isEmpty()) {
            // Try to EDIT the existing message — never create a new one
            channel.editMessageEmbedsById(statusMessageId, embed.build()).queue(
                    success -> {
                        /* edited in-place */ },
                    error -> {
                        // Message was deleted or doesn't exist — send ONE new message
                        plugin.debug("Status message " + statusMessageId + " missing, sending new one.");
                        statusMessageId = null;
                        sendNewStatus(channel, embed);
                    });
        } else {
            sendNewStatus(channel, embed);
        }
    }

    private void sendNewStatus(TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build()).queue(
                message -> {
                    statusMessageId = message.getId();
                    // Persist the message ID so it survives restarts
                    persistMessageId(statusMessageId);
                },
                error -> plugin.debug("Failed to send status embed: " + error.getMessage()));
    }

    private void persistMessageId(String messageId) {
        try {
            java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
            org.bukkit.configuration.file.YamlConfiguration config = org.bukkit.configuration.file.YamlConfiguration
                    .loadConfiguration(configFile);
            config.set("channels.status-message", messageId);
            config.save(configFile);
            plugin.getConfigManager().reload();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save status message ID: " + e.getMessage());
        }
    }

    private EmbedBuilder buildStatusEmbed() {
        String title = plugin.getConfigManager().getString("status.embed.title", "🎮 Server Status");
        String colorHex = plugin.getConfigManager().getString("status.embed.color", "#5865F2");
        String serverIp = plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com");

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double[] tps = dev.demonz.zdiscord.util.TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;

        // TPS color indicator
        String tpsIndicator = tps[0] >= 18 ? "🟢" : tps[0] >= 15 ? "🟡" : "🔴";

        // Build progress bar for player count
        int barLength = 20;
        int filled = max > 0 ? (int) ((online / (double) max) * barLength) : 0;
        String bar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barLength - filled));

        // Get the Discord server's icon (not an external API)
        String thumbnailUrl = null;
        Guild guild = plugin.getBotManager().getGuild();
        if (guild != null && guild.getIconUrl() != null) {
            thumbnailUrl = guild.getIconUrl() + "?size=128";
        } else if (plugin.getBotManager().getJda() != null) {
            thumbnailUrl = plugin.getBotManager().getJda().getSelfUser().getEffectiveAvatarUrl();
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setColor(Color.decode(colorHex))
                .addField("📊 Status", "🟢 **Online**", true)
                .addField("🌐 Server IP", "`" + serverIp + "`", true)
                .addField("\u200B", "\u200B", true) // Spacer
                .addField("👥 Players [" + online + "/" + max + "]", "`" + bar + "`", false);

        if (plugin.getConfigManager().getBoolean("status.embed.show-players", true) && online > 0) {
            String playerList = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .sorted()
                    .limit(20)
                    .collect(Collectors.joining(", "));
            if (online > 20)
                playerList += " +" + (online - 20) + " more";
            embed.addField("🎮 Online Players", playerList, false);
        }

        if (plugin.getConfigManager().getBoolean("status.embed.show-tps", true)) {
            embed.addField(tpsIndicator + " TPS", String.format("%.1f / 20.0", tps[0]), true);
        }

        if (plugin.getConfigManager().getBoolean("status.embed.show-memory", true)) {
            int memPercent = maxMb > 0 ? (int) ((usedMb * 100.0) / maxMb) : 0;
            String memEmoji = memPercent < 70 ? "🟢" : (memPercent < 85 ? "🟡" : "🔴");
            embed.addField(memEmoji + " Memory", usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)", true);
        }

        embed.setFooter(
                "ZDiscord • Auto-updates every " + plugin.getConfigManager().getInt("status.update-interval", 30) + "s")
                .setTimestamp(Instant.now());

        if (thumbnailUrl != null) {
            embed.setThumbnail(thumbnailUrl);
        }

        return embed;
    }

    public void reload() {
        statusMessageId = plugin.getConfigManager().getString("channels.status-message", null);
    }

    public void shutdown() {
        // Send an "Offline" status embed when the server shuts down
        if (statusMessageId == null || statusMessageId.isEmpty())
            return;
        if (!plugin.getBotManager().isConnected())
            return;

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.status");
        if (channel == null)
            return;

        String serverIp = plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com");

        // Get guild icon
        String thumbnailUrl = null;
        Guild guild = plugin.getBotManager().getGuild();
        if (guild != null && guild.getIconUrl() != null) {
            thumbnailUrl = guild.getIconUrl() + "?size=128";
        }

        EmbedBuilder offline = new EmbedBuilder()
                .setTitle(plugin.getConfigManager().getString("status.embed.title", "🎮 Server Status"))
                .setColor(Color.decode("#E74C3C"))
                .addField("📊 Status", "🔴 **Offline**", true)
                .addField("🌐 Server IP", "`" + serverIp + "`", true)
                .addField("\u200B", "\u200B", true)
                .addField("👥 Players [0/0]", "`" + "░".repeat(20) + "`", false)
                .setFooter("ZDiscord • Server is offline")
                .setTimestamp(Instant.now());

        if (thumbnailUrl != null) {
            offline.setThumbnail(thumbnailUrl);
        }

        try {
            channel.editMessageEmbedsById(statusMessageId, offline.build()).complete();
        } catch (Exception e) {
            plugin.debug("Failed to send offline status: " + e.getMessage());
        }
    }
}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ServerBridge;
import dev.demonz.zdiscord.util.TPSUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
/**
 * One Discord message that rolls the whole server into a single embed:
 * players, performance, and the current leaderboards, refreshed on a timer.
 * The status/performance/leaderboard panels each cover their own slice;
 * this is the at-a-glance combined view.
 */
public class LiveStatsModule {

    private static final int NAME_LIST_MAX = 15;

    private final ZDiscord plugin;
    private String messageId;
    private volatile boolean running = true;

    public LiveStatsModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        messageId = loadMessageId();
        int interval = plugin.getConfigManager().getInt("live-stats.update-interval", 60);
        plugin.getPlatformAdapter().runTimer(this::update, 200L, Math.max(2, interval) * 20L);
    }

    public void reload() {
        messageId = loadMessageId();
    }

    public void shutdown() {
        running = false;
    }

    private void update() {
        if (!running) return;
        TextChannel channel = plugin.getBotManager().getTextChannel("live-stats.channel");
        if (channel == null) {
            return;
        }

        EmbedBuilder embed = buildEmbed();
        if (messageId != null && !messageId.isEmpty()) {
            channel.editMessageEmbedsById(messageId, embed.build()).queue(
                    success -> { },
                    error -> {
                        plugin.debug("Live stats message " + messageId
                                + " is gone, creating a new one.");
                        messageId = null;
                        sendNew(channel, embed);
                    });
        } else {
            sendNew(channel, embed);
        }
    }

    private void sendNew(TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build()).queue(
                msg -> {
                    messageId = msg.getId();
                    persistMessageId(messageId);
                },
                error -> plugin.debug("Failed to send live stats embed: " + error.getMessage()));
    }

    private EmbedBuilder buildEmbed() {
        double tps = TPSUtil.getTPS()[0];
        boolean online = tps > 0.0;

        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        int memPercent = maxMb > 0 ? (int) ((usedMb * 100.0) / maxMb) : 0;

        int onlineCount = ServerBridge.onlinePlayers().size();
        int maxCount = ServerBridge.maxPlayers();

        double tpsWarning = plugin.getConfigManager().getDouble("performance.tps-warning", 18.0);
        double tpsCritical = plugin.getConfigManager().getDouble("performance.tps-critical", 15.0);
        Color color = tps >= tpsWarning && memPercent < 90 ? new Color(0x2ECC71)
                : tps >= tpsCritical ? new Color(0xF1C40F) : new Color(0xE74C3C);

        String serverIp = plugin.getConfigManager().getString(
                "status.embed.server-ip", "play.yourserver.com");

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(online ? serverIp : serverIp + " (restarting)", null, guildIcon())
                .setTitle(online ? "📈 Live Server Stats"
                        : "🔴 Live Server Stats")
                .setColor(color)
                .setTimestamp(Instant.now());

        if (!online) {
            embed.setDescription("The server is offline or starting up.");
            return embed.setFooter("Auto-updates \u2022 ZDiscord");
        }

        embed.addField("👥 Players",
                "**" + onlineCount + "** / " + maxCount, true);
        embed.addField("🌡️ TPS",
                String.format(Locale.ROOT, "`%.1f`", tps)
                        + (tps >= tpsWarning ? " ✅"
                        : tps >= tpsCritical ? " ⚠️" : " ⛔️"), true);
        embed.addField("🧠 Memory",
                usedMb + "/" + maxMb + "MB (" + memPercent + "%)", true);

        String names = playerNames();
        if (!names.isEmpty()) {
            embed.addField("📜 Online Now", names, false);
        }

        LeaderboardModule boards = plugin.getLeaderboardModule();
        if (boards != null) {
            embed.addField("⚔️ Top Kills",
                    rankedLines(boards, "kills", 5), true);
            embed.addField("🕐 Most Playtime",
                    rankedLines(boards, "playtime", 5), true);
        }

        List<Map.Entry<UUID, Integer>> followed =
                plugin.getStorageManager().getTopFollowedPlayers(3);
        if (!followed.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < followed.size(); i++) {
                var entry = followed.get(i);
                String name = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
                if (name == null) name = "Unknown";
                if (i > 0) sb.append("\n");
                sb.append("**").append(name).append("** \u2014 ")
                        .append(entry.getValue()).append(" follower")
                        .append(entry.getValue() == 1 ? "" : "s");
            }
            embed.addField("🔔 Most Followed", sb.toString(), true);
        }

        int interval = plugin.getConfigManager().getInt("live-stats.update-interval", 60);
        return embed.setFooter("Auto-updates every " + interval + "s \u2022 ZDiscord");
    }

    private String rankedLines(LeaderboardModule boards, String stat, int limit) {
        List<Map.Entry<UUID, Long>> top = boards.getLeaderboard(stat, limit);
        if (top.isEmpty()) {
            return "*no data yet*";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < top.size(); i++) {
            String name = plugin.getServer().getOfflinePlayer(top.get(i).getKey()).getName();
            if (name == null) name = "Unknown";
            if (i > 0) sb.append("\n");
            sb.append("**").append(i + 1).append(".** ").append(name);
            if ("playtime".equals(stat)) {
                long secs = top.get(i).getValue();
                sb.append(" \u2014 ").append(secs / 3600).append("h")
                        .append((secs % 3600) / 60).append("m");
            } else {
                sb.append(" \u2014 ").append(top.get(i).getValue());
            }
        }
        return sb.toString();
    }

    private String playerNames() {
        StringBuilder list = new StringBuilder();
        int shown = 0;
        try {
            for (Player p : ServerBridge.onlinePlayers()) {
                if (p == null) continue;
                if (shown > 0) list.append(", ");
                list.append("**").append(p.getName()).append("**");
                if (++shown >= NAME_LIST_MAX) break;
            }
        } catch (ConcurrentModificationException e) {
            return "";
        }
        if (shown == 0) {
            return "*nobody online right now*";
        }
        int total = ServerBridge.onlinePlayers().size();
        if (total > shown) {
            list.append(" *and ").append(total - shown).append(" more*");
        }
        return list.toString();
    }

    private String guildIcon() {
        try {
            var guild = plugin.getBotManager().getGuild();
            if (guild != null && guild.getIconUrl() != null) {
                return guild.getIconUrl() + "?size=64";
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "live_stats_data.yml");
    }

    private void persistMessageId(String id) {
        try {
            File f = dataFile();
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            cfg.set("live-stats-message-id", id);
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save live stats message ID: " + e.getMessage());
        }
    }

    private String loadMessageId() {
        File f = dataFile();
        if (!f.exists()) {
            return null;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return cfg.getString("live-stats-message-id", null);
    }
}

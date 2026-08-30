package dev.demonz.zdiscord.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import java.util.function.Supplier;

public final class StatusEmbedBuilder {

    private static final int BAR_LENGTH = 14;
    private static final String BAR_FULL = "\u2588";
    private static final String BAR_EMPTY = "\u2591";

    private static final int PLAYER_LIST_MAX = 12;
    private static final String THUMBNAIL_SIZE = "?size=256";

    private StatusEmbedBuilder() {
    }

    public static EmbedBuilder builder(StatusContext ctx) {
        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(ctx.serverIp == null || ctx.serverIp.isEmpty()
                                ? "Minecraft Server" : ctx.serverIp, null, ctx.guildIconUrl)
                .setTitle(ctx.online ? "🟢 Server Online" : "🔴 Server Offline")
                .setColor(ctx.online ? healthyColor(ctx) : new Color(0xE74C3C))
                .setThumbnail(ctx.guildIconUrl);

        embed.addField("Status", ctx.online ? "✅ Online" : "❌ Offline", true);

        if (ctx.online) {
            int memPercent = ctx.maxMemoryMb > 0
                    ? (int) ((ctx.usedMemoryMb * 100.0) / ctx.maxMemoryMb) : 0;

            if (ctx.showPlayers) {
                embed.addField("Players",
                        "**" + ctx.onlineCount + "** / " + ctx.maxCount
                                + "\n`" + playerBar(ctx.onlineCount, ctx.maxCount) + "`", true);
            }

            if (ctx.showTps) {
                embed.addField("TPS",
                        "`" + String.format(Locale.ROOT, "%.1f", ctx.tps) + "` / 20.0"
                                + (ctx.tps >= ctx.tpsWarning ? "  ✅"
                                : ctx.tps >= ctx.tpsCritical ? "  ⚠️" : "  ⛔️"), true);
            }

            if (ctx.showMemory) {
                embed.addField("Memory",
                        String.format(Locale.ROOT, "`%dMB` / `%dMB` (%d%%)\n`%s`",
                                ctx.usedMemoryMb, ctx.maxMemoryMb, memPercent,
                                memoryBar(memPercent)), false);
            }

            if (ctx.showPlayers && ctx.onlineCount > 0 && ctx.playerList != null) {
                embed.addField("👥 Online Players", ctx.playerList, false);
            }
        }

        return embed.setFooter("Auto-updates every " + ctx.updateIntervalSeconds
                        + "s \u2022 ZDiscord")
                .setTimestamp(Instant.now());
    }

    public static MessageEmbed build(StatusContext ctx) {
        return builder(ctx).build();
    }

    private static String playerBar(int online, int max) {
        if (max <= 0) return BAR_EMPTY.repeat(BAR_LENGTH);
        int filled = (int) Math.round((online / (double) max) * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        return BAR_FULL.repeat(filled) + BAR_EMPTY.repeat(BAR_LENGTH - filled);
    }

    private static String memoryBar(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round((clamped / 100.0) * BAR_LENGTH);
        return BAR_FULL.repeat(filled) + BAR_EMPTY.repeat(BAR_LENGTH - filled);
    }

    private static Color healthyColor(StatusContext ctx) {
        if (ctx.tps < ctx.tpsCritical) return new Color(0xE74C3C);
        if (ctx.tps < ctx.tpsWarning) return new Color(0xF39C12);
        if (ctx.maxMemoryMb > 0) {
            int memPercent = (int) ((ctx.usedMemoryMb * 100.0) / ctx.maxMemoryMb);
            if (memPercent >= 90) return new Color(0xE74C3C);
            if (memPercent >= 75) return new Color(0xF39C12);
        }
        return new Color(0x2ECC71);
    }

    public static final class StatusContext {
        public String title;
        public Color color;
        public String serverIp;
        public boolean online;
        public int onlineCount;
        public int maxCount;
        public boolean showPlayers;
        public boolean showTps;
        public boolean showMemory;
        public String playerList;
        public double tps;
        public double tpsWarning;
        public double tpsCritical;
        public long usedMemoryMb;
        public long maxMemoryMb;
        public int updateIntervalSeconds;
        public String guildIconUrl;
        public String botAvatarUrl;

        public static StatusContext capture(Supplier<Guild> guildSupplier,
                                            String title, String colorHex, String serverIp,
                                            int updateInterval, boolean showPlayers,
                                            boolean showTps, boolean showMemory,
                                            double tpsWarning, double tpsCritical) {
            StatusContext ctx = new StatusContext();
            ctx.title = title;
            ctx.color = ColorUtil.parseHex(colorHex);
            ctx.serverIp = serverIp;
            ctx.updateIntervalSeconds = updateInterval;
            ctx.showPlayers = showPlayers;
            ctx.showTps = showTps;
            ctx.showMemory = showMemory;
            ctx.tpsWarning = tpsWarning;
            ctx.tpsCritical = tpsCritical;

            ctx.onlineCount = ServerBridge.onlinePlayers().size();
            ctx.maxCount = ServerBridge.maxPlayers();
            ctx.online = true;

            Runtime runtime = Runtime.getRuntime();
            ctx.usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            ctx.maxMemoryMb = runtime.maxMemory() / 1024 / 1024;
            ctx.tps = TPSUtil.getTPS()[0];

            if (showPlayers && ctx.onlineCount > 0) {
                StringBuilder list = new StringBuilder();
                int shown = 0;
                boolean raced = false;
                // the live collection can be mutated by joins/quits while we
                // read it; if that happens just skip the list this refresh
                try {
                    for (Player p : ServerBridge.onlinePlayers()) {
                        if (p == null) continue;
                        if (shown > 0) list.append("\n");
                        list.append("`").append(p.getName()).append("`");
                        if (++shown >= PLAYER_LIST_MAX) break;
                    }
                } catch (ConcurrentModificationException e) {
                    raced = true;
                }
                if (raced) {
                    ctx.playerList = null;
                } else {
                    if (ctx.onlineCount > PLAYER_LIST_MAX) {
                        list.append("\n*...and ")
                                .append(ctx.onlineCount - PLAYER_LIST_MAX).append(" more*");
                    }
                    ctx.playerList = list.toString();
                }
            } else {
                ctx.playerList = null;
            }

            Guild guild = guildSupplier.get();
            if (guild != null && guild.getIconUrl() != null) {
                String url = guild.getIconUrl();
                if (url.contains("?")) {
                    url = url.substring(0, url.indexOf('?'));
                }
                ctx.guildIconUrl = url + THUMBNAIL_SIZE;
            }
            return ctx;
        }
    }
}

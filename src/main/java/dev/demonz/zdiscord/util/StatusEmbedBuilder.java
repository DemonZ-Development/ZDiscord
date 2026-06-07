/*
 * Copyright 2026 DemonZ Development
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

package dev.demonz.zdiscord.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;

/**
 * Builds the standard server status embed that is used by the
 * status module, the /setup wizard, and the /status slash command.
 */
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
                                ? "Minecraft Server" : ctx.serverIp,
                        null,
                        ctx.guildIconUrl)
                .setTitle(ctx.online ? ":green_circle: Server Online" : ":red_circle: Server Offline")
                .setColor(ctx.online ? healthyColor(ctx) : new java.awt.Color(0xE74C3C))
                .setThumbnail(ctx.guildIconUrl);

        embed.addField("Status",
                (ctx.online ? ":white_check_mark: Online" : ":x: Offline"),
                true);

        if (ctx.online) {
            int memPercent = ctx.maxMemoryMb > 0
                    ? (int) ((ctx.usedMemoryMb * 100.0) / ctx.maxMemoryMb) : 0;

            if (ctx.showPlayers) {
                embed.addField("Players",
                        "**" + ctx.onlineCount + "** / " + ctx.maxCount
                                + "\n`" + playerBar(ctx.onlineCount, ctx.maxCount) + "`",
                        true);
            }

            if (ctx.showTps) {
                embed.addField("TPS",
                        "`" + String.format("%.1f", ctx.tps) + "` / 20.0"
                                + (ctx.tps >= ctx.tpsWarning ? "  :white_check_mark:"
                                        : ctx.tps >= ctx.tpsCritical ? "  :warning:"
                                                : "  :no_entry:"),
                        true);
            }

            if (ctx.showMemory) {
                embed.addField("Memory",
                        String.format("`%dMB` / `%dMB` (%d%%)\n`%s`",
                                ctx.usedMemoryMb, ctx.maxMemoryMb, memPercent,
                                memoryBar(memPercent)),
                        false);
            }

            if (ctx.showPlayers && ctx.onlineCount > 0 && ctx.playerList != null) {
                embed.addField(":busts_in_silhouette: Online Players",
                        ctx.playerList, false);
            }
        }

        embed.setFooter("Auto-updates every " + ctx.updateIntervalSeconds
                        + "s \u2022 ZDiscord")
                .setTimestamp(Instant.now());
        return embed;
    }

    public static MessageEmbed build(StatusContext ctx) {
        return builder(ctx).build();
    }

    private static String playerBar(int online, int max) {
        if (max <= 0) {
            return BAR_EMPTY.repeat(BAR_LENGTH);
        }
        int filled = (int) Math.round((online / (double) max) * BAR_LENGTH);
        if (filled < 0) {
            filled = 0;
        } else if (filled > BAR_LENGTH) {
            filled = BAR_LENGTH;
        }
        return BAR_FULL.repeat(filled) + BAR_EMPTY.repeat(BAR_LENGTH - filled);
    }

    private static String memoryBar(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        int filled = (int) Math.round((clamped / 100.0) * BAR_LENGTH);
        return BAR_FULL.repeat(filled) + BAR_EMPTY.repeat(BAR_LENGTH - filled);
    }

    private static java.awt.Color healthyColor(StatusContext ctx) {
        if (ctx.tps < ctx.tpsCritical) {
            return new java.awt.Color(0xE74C3C);
        }
        if (ctx.tps < ctx.tpsWarning) {
            return new java.awt.Color(0xF39C12);
        }
        if (ctx.maxMemoryMb > 0) {
            int memPercent = (int) ((ctx.usedMemoryMb * 100.0) / ctx.maxMemoryMb);
            if (memPercent >= 90) {
                return new java.awt.Color(0xE74C3C);
            }
            if (memPercent >= 75) {
                return new java.awt.Color(0xF39C12);
            }
        }
        return new java.awt.Color(0x2ECC71);
    }

    /**
     * Inputs needed to build a status embed. Constructed via {@link #capture}.
     */
    public static final class StatusContext {
        public String title;
        public java.awt.Color color;
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

        public static StatusContext capture(java.util.function.Supplier<Guild> guildSupplier,
                                            String title,
                                            String colorHex,
                                            String serverIp,
                                            int updateInterval,
                                            boolean showPlayers,
                                            boolean showTps,
                                            boolean showMemory,
                                            double tpsWarning,
                                            double tpsCritical) {
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
                for (org.bukkit.entity.Player p : ServerBridge.onlinePlayers()) {
                    if (shown > 0) {
                        list.append("\n");
                    }
                    list.append("`").append(p.getName()).append("`");
                    shown++;
                    if (shown >= PLAYER_LIST_MAX) {
                        break;
                    }
                }
                if (ctx.onlineCount > PLAYER_LIST_MAX) {
                    list.append("\n*...and ")
                            .append(ctx.onlineCount - PLAYER_LIST_MAX)
                            .append(" more*");
                }
                ctx.playerList = list.toString();
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

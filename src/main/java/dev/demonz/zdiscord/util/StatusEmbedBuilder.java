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

package dev.demonz.zdiscord.util;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.Bukkit;

import java.time.Instant;

/**
 * Builds the standard server status embed that is used by the
 * status module, the /setup wizard, and the /status slash command.
 */
public final class StatusEmbedBuilder {

    private static final int BAR_LENGTH = 20;
    private static final String THUMBNAIL_SIZE = "?size=128";

    private StatusEmbedBuilder() {
    }

    public static EmbedBuilder builder(StatusContext ctx) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(ctx.title)
                .setColor(ctx.color)
                .addField("Status", ctx.online ? "Online" : "Offline", true)
                .addField("Server IP", "`" + ctx.serverIp + "`", true)
                .addField("Players [" + ctx.onlineCount + "/" + ctx.maxCount + "]",
                        "`" + playerBar(ctx.onlineCount, ctx.maxCount) + "`", false);

        if (ctx.showPlayers && ctx.onlineCount > 0) {
            embed.addField("Online", ctx.playerList, false);
        }
        if (ctx.showTps) {
            String tpsIndicator = ctx.tps >= ctx.tpsWarning ? ""
                    : ctx.tps >= ctx.tpsCritical ? "!" : "!!";
            embed.addField("TPS" + tpsIndicator,
                    String.format("%.1f / 20.0", ctx.tps), true);
        }
        if (ctx.showMemory) {
            int memPercent = ctx.maxMemoryMb > 0
                    ? (int) ((ctx.usedMemoryMb * 100.0) / ctx.maxMemoryMb) : 0;
            embed.addField("Memory",
                    ctx.usedMemoryMb + "MB / " + ctx.maxMemoryMb + "MB (" + memPercent + "%)", true);
        }
        embed.setFooter("Auto-updates every " + ctx.updateIntervalSeconds + "s")
                .setTimestamp(Instant.now());

        String thumbnail = resolveThumbnail(ctx.guildIconUrl, ctx.botAvatarUrl);
        if (thumbnail != null) {
            embed.setThumbnail(thumbnail);
        }
        return embed;
    }

    public static MessageEmbed build(StatusContext ctx) {
        return builder(ctx).build();
    }

    private static String playerBar(int online, int max) {
        if (max <= 0) {
            return " ".repeat(BAR_LENGTH);
        }
        int filled = (int) ((online / (double) max) * BAR_LENGTH);
        if (filled < 0) {
            filled = 0;
        } else if (filled > BAR_LENGTH) {
            filled = BAR_LENGTH;
        }
        return "#".repeat(filled) + "-".repeat(BAR_LENGTH - filled);
    }

    private static String resolveThumbnail(String guildIconUrl, String botAvatarUrl) {
        if (guildIconUrl != null && !guildIconUrl.isEmpty()) {
            return guildIconUrl + THUMBNAIL_SIZE;
        }
        return botAvatarUrl;
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

            ctx.onlineCount = Bukkit.getOnlinePlayers().size();
            ctx.maxCount = Bukkit.getMaxPlayers();
            // If the status module is running, the server is up.
            ctx.online = true;

            Runtime runtime = Runtime.getRuntime();
            ctx.usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
            ctx.maxMemoryMb = runtime.maxMemory() / 1024 / 1024;
            ctx.tps = TPSUtil.getTPS()[0];

            if (showPlayers && ctx.onlineCount > 0) {
                StringBuilder list = new StringBuilder();
                int shown = 0;
                for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                    if (shown > 0) {
                        list.append(", ");
                    }
                    list.append(p.getName());
                    shown++;
                    if (shown >= 20) {
                        break;
                    }
                }
                if (ctx.onlineCount > 20) {
                    list.append(" +").append(ctx.onlineCount - 20).append(" more");
                }
                ctx.playerList = list.toString();
            } else {
                ctx.playerList = "No players online";
            }

            Guild guild = guildSupplier.get();
            if (guild != null && guild.getIconUrl() != null) {
                ctx.guildIconUrl = guild.getIconUrl();
            }
            return ctx;
        }
    }
}

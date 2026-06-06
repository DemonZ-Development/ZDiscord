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

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.TPSUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/**
 * Periodically edits a single Discord message with the server's
 * current TPS, memory usage, and a small history graph.
 */
public class PerformanceModule {

    private static final int HISTORY_SIZE = 30;

    private final ZDiscord plugin;
    private final Deque<Double> tpsHistory = new ArrayDeque<>(HISTORY_SIZE);
    private final Deque<Long> memoryHistory = new ArrayDeque<>(HISTORY_SIZE);
    private String perfMessageId;

    public PerformanceModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        int interval = plugin.getConfigManager().getInt("performance.update-interval", 60);
        plugin.getPlatformAdapter().runAsyncTimer(this::updatePerformance, 200L, interval * 20L);
    }

    private void updatePerformance() {
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.performance");
        if (channel == null) {
            return;
        }

        double[] tps = TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        int memPercent = maxMb > 0 ? (int) ((usedMb * 100.0) / maxMb) : 0;

        tpsHistory.addLast(tps[0]);
        memoryHistory.addLast((long) memPercent);
        if (tpsHistory.size() > HISTORY_SIZE) tpsHistory.pollFirst();
        if (memoryHistory.size() > HISTORY_SIZE) memoryHistory.pollFirst();

        double tpsWarning = plugin.getConfigManager().getDouble("performance.tps-warning", 18.0);
        double tpsCritical = plugin.getConfigManager().getDouble("performance.tps-critical", 15.0);
        int memWarning = plugin.getConfigManager().getInt("performance.memory-warning", 80);

        String overall;
        int color;
        if (tps[0] >= tpsWarning && memPercent < memWarning) {
            overall = "Healthy";
            color = 0x2ECC71;
        } else if (tps[0] >= tpsCritical && memPercent < 90) {
            overall = "Warning";
            color = 0xF1C40F;
        } else {
            overall = "Critical";
            color = 0xE74C3C;
        }

        String tpsGraph = buildGraph(toArray(tpsHistory), 20.0);
        String memGraph = buildGraph(memoryHistory.stream().mapToDouble(Long::doubleValue).toArray(), 100.0);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Server Performance")
                .setColor(color)
                .addField("Overall", overall, true)
                .addField("Players", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
                .addField("Threads", String.valueOf(Thread.activeCount()), true)
                .addField("TPS (1m / 5m / 15m)",
                        String.format("`%.2f` / `%.2f` / `%.2f`", tps[0], tps[1], tps[2]), false)
                .addField("TPS history", "```\n" + tpsGraph + "\n```", false)
                .addField("Memory",
                        usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)", false)
                .addField("Memory history", "```\n" + memGraph + "\n```", false)
                .setTimestamp(Instant.now());

        if (tps[0] < tpsCritical) {
            embed.addField("Alert", "TPS is critically low. Server may be lagging.", false);
        }
        if (memPercent >= 90) {
            embed.addField("Alert", "Memory usage is critically high.", false);
        }

        if (perfMessageId != null) {
            channel.editMessageEmbedsById(perfMessageId, embed.build()).queue(
                    success -> { },
                    error -> {
                        perfMessageId = null;
                        channel.sendMessageEmbeds(embed.build()).queue(
                                msg -> perfMessageId = msg.getId());
                    });
        } else {
            channel.sendMessageEmbeds(embed.build()).queue(msg -> perfMessageId = msg.getId());
        }
    }

    private static double[] toArray(Deque<Double> deque) {
        double[] out = new double[deque.size()];
        int i = 0;
        for (Double d : deque) {
            out[i++] = d;
        }
        return out;
    }

    private String buildGraph(double[] values, double max) {
        if (values.length == 0) {
            return "No data";
        }
        int height = 5;
        int width = Math.min(values.length, HISTORY_SIZE);
        char[][] grid = new char[height][width];
        for (char[] row : grid) {
            Arrays.fill(row, ' ');
        }
        for (int i = 0; i < width; i++) {
            int idx = values.length - width + i;
            if (idx < 0) {
                continue;
            }
            int bar = (int) ((values[idx] / max) * height);
            if (bar > height) {
                bar = height;
            }
            for (int j = height - bar; j < height; j++) {
                grid[j][i] = '#';
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < height; j++) {
            String label;
            if (j == 0) {
                label = String.format("%4.0f|", max);
            } else if (j == height - 1) {
                label = "   0|";
            } else {
                label = "    |";
            }
            sb.append(label).append(new String(grid[j])).append('\n');
        }
        sb.append("    +").append("-".repeat(width));
        return sb.toString();
    }

    public void reload() {
    }

    public void shutdown() {
    }
}

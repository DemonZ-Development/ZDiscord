package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;

import java.awt.*;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Performance monitoring module.
 * Posts TPS, MSPT, and memory usage metrics to a Discord channel.
 */
public class PerformanceModule {

    private final ZDiscord plugin;
    private final Queue<Double> tpsHistory = new LinkedList<>();
    private final Queue<Long> memoryHistory = new LinkedList<>();
    private String perfMessageId;

    public PerformanceModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        int interval = plugin.getConfigManager().getInt("performance.update-interval", 60);
        long ticks = interval * 20L;

        plugin.getPlatformAdapter().runAsyncTimer(this::updatePerformance, 200L, ticks);
    }

    private void updatePerformance() {
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.performance");
        if (channel == null)
            return;

        double[] tps = dev.demonz.zdiscord.util.TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        int memPercent = (int) ((usedMb * 100.0) / maxMb);

        // Track history
        tpsHistory.add(tps[0]);
        memoryHistory.add(usedMb);
        if (tpsHistory.size() > 30)
            tpsHistory.poll();
        if (memoryHistory.size() > 30)
            memoryHistory.poll();

        // Determine thresholds
        double tpsWarning = plugin.getConfigManager().getDouble("performance.tps-warning", 18.0);
        double tpsCritical = plugin.getConfigManager().getDouble("performance.tps-critical", 15.0);
        int memWarning = plugin.getConfigManager().getInt("performance.memory-warning", 80);

        String tpsEmoji = tps[0] >= tpsWarning ? "🟢" : (tps[0] >= tpsCritical ? "🟡" : "🔴");
        String memEmoji = memPercent < memWarning ? "🟢" : (memPercent < 90 ? "🟡" : "🔴");
        String overallStatus = (tps[0] >= tpsWarning && memPercent < memWarning) ? "🟢 Healthy"
                : (tps[0] >= tpsCritical && memPercent < 90) ? "🟡 Warning" : "🔴 Critical";

        // Build TPS graph
        String tpsGraph = buildGraph(tpsHistory.stream().mapToDouble(Double::doubleValue).toArray(), 20.0);
        String memGraph = buildMemGraph(memoryHistory.stream().mapToLong(Long::longValue).toArray(), maxMb);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Server Performance Monitor")
                .setColor(tps[0] >= tpsWarning ? Color.decode("#2ECC71")
                        : (tps[0] >= tpsCritical ? Color.decode("#F1C40F") : Color.decode("#E74C3C")))
                .addField("Overall Status", overallStatus, true)
                .addField("👥 Online", String.valueOf(Bukkit.getOnlinePlayers().size()), true)
                .addField("🧵 Threads", String.valueOf(Thread.activeCount()), true)
                .addField(tpsEmoji + " TPS (1m / 5m / 15m)",
                        String.format("`%.2f` / `%.2f` / `%.2f`", tps[0], tps[1], tps[2]), false)
                .addField("📈 TPS History", "```\n" + tpsGraph + "\n```", false)
                .addField(memEmoji + " Memory",
                        usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)", false)
                .addField("📈 Memory History", "```\n" + memGraph + "\n```", false)
                .setFooter("ZDiscord • Performance Monitor")
                .setTimestamp(Instant.now());

        // Check for critical alerts
        if (tps[0] < tpsCritical) {
            embed.addField("⚠️ ALERT", "TPS is critically low! Server may be lagging.", false);
        }
        if (memPercent >= 90) {
            embed.addField("⚠️ ALERT", "Memory usage is critically high!", false);
        }

        if (perfMessageId != null) {
            channel.editMessageEmbedsById(perfMessageId, embed.build()).queue(
                    success -> {
                    },
                    error -> {
                        perfMessageId = null;
                        channel.sendMessageEmbeds(embed.build()).queue(
                                msg -> perfMessageId = msg.getId());
                    });
        } else {
            channel.sendMessageEmbeds(embed.build()).queue(
                    msg -> perfMessageId = msg.getId());
        }
    }

    /**
     * Build a simple ASCII graph from TPS values.
     */
    private String buildGraph(double[] values, double max) {
        if (values.length == 0)
            return "No data";

        int height = 5;
        int width = Math.min(values.length, 30);
        char[][] grid = new char[height][width];
        for (char[] row : grid)
            java.util.Arrays.fill(row, ' ');

        for (int i = 0; i < width; i++) {
            int idx = values.length - width + i;
            if (idx < 0)
                continue;
            int bar = (int) ((values[idx] / max) * height);
            bar = Math.min(bar, height);
            for (int j = height - bar; j < height; j++) {
                grid[j][i] = '█';
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int j = 0; j < height; j++) {
            String label = j == 0 ? String.format("%4.0f│", max) : j == height - 1 ? "   0│" : "    │";
            sb.append(label);
            sb.append(new String(grid[j]));
            sb.append('\n');
        }
        sb.append("    └").append("─".repeat(width));
        return sb.toString();
    }

    private String buildMemGraph(long[] values, long max) {
        if (values.length == 0)
            return "No data";

        double[] normalized = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            normalized[i] = (values[i] / (double) max) * 100;
        }
        return buildGraph(normalized, 100);
    }

    public void reload() {
        // Config values (thresholds) are read on each update cycle — no specific reload
        // needed
    }

    public void shutdown() {
        // Nothing to clean up
    }
}

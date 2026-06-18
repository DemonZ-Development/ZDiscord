package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.TPSUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.logging.Level;


public class PerformanceModule {

    private static final int HISTORY_SIZE = 30;

    private static final String SPARK_BLOCKS =
            "\u2581\u2582\u2583\u2584\u2585\u2586\u2587\u2588";

    private final ZDiscord plugin;
    private final Deque<Double> tpsHistory = new ArrayDeque<>(HISTORY_SIZE);
    private final Deque<Integer> memoryHistory = new ArrayDeque<>(HISTORY_SIZE);
    private String perfMessageId;
    private volatile boolean running = true;

    public PerformanceModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        perfMessageId = loadMessageId();
        int interval = plugin.getConfigManager().getInt("performance.update-interval", 60);
        plugin.getPlatformAdapter().runAsyncTimer(this::updatePerformance, 200L, interval * 20L);
    }

    private void updatePerformance() {
        if (!running) return;
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
        memoryHistory.addLast(memPercent);
        if (tpsHistory.size() > HISTORY_SIZE) tpsHistory.pollFirst();
        if (memoryHistory.size() > HISTORY_SIZE) memoryHistory.pollFirst();

        double tpsWarning = plugin.getConfigManager().getDouble("performance.tps-warning", 18.0);
        double tpsCritical = plugin.getConfigManager().getDouble("performance.tps-critical", 15.0);
        int memWarning = plugin.getConfigManager().getInt("performance.memory-warning", 80);

        int color;
        String health;
        String healthEmoji;
        if (tps[0] >= tpsWarning && memPercent < memWarning) {
            color = 0x2ECC71;
            health = "Healthy";
            healthEmoji = ":white_check_mark:";
        } else if (tps[0] >= tpsCritical && memPercent < 90) {
            color = 0xF1C40F;
            health = "Warning";
            healthEmoji = ":warning:";
        } else {
            color = 0xE74C3C;
            health = "Critical";
            healthEmoji = ":no_entry:";
        }

        String tpsSpark = buildSparkline(toDoubleArray(tpsHistory), 20.0);
        String memSpark = buildSparkline(toIntArray(memoryHistory), 100.0);

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor("Server Performance", null, plugin.getBotManager().getJda() != null
                        ? plugin.getBotManager().getJda().getSelfUser().getEffectiveAvatarUrl()
                        : null)
                .setTitle(healthEmoji + " " + health)
                .setColor(color)
                .addField("TPS (1m / 5m / 15m)",
                        String.format("`%.2f` / `%.2f` / `%.2f`",
                                tps[0], tps[1], tps[2]), true)
                .addField("Memory",
                        String.format("`%dMB` / `%dMB` (%d%%)",
                                usedMb, maxMb, memPercent), true)
                .addField("Players / Threads",
                        Bukkit.getOnlinePlayers().size() + " / " + Thread.activeCount(),
                        true)
                .addField("TPS History",
                        tpsSpark.isEmpty() ? "*Collecting...*"
                                : "`" + tpsSpark + "` *20.0*", false)
                .addField("Memory History",
                        memSpark.isEmpty() ? "*Collecting...*"
                                : "`" + memSpark + "` *100%*", false)
                .setTimestamp(Instant.now());

        if (tps[0] < tpsCritical) {
            embed.addField(":rotating_light: Alert", "TPS is critically low.", false);
        }
        if (memPercent >= 90) {
            embed.addField(":rotating_light: Alert", "Memory usage is critically high.", false);
        }

        if (perfMessageId != null) {
            channel.editMessageEmbedsById(perfMessageId, embed.build()).queue(
                    success -> { },
                    error -> {
                        perfMessageId = null;
                        channel.sendMessageEmbeds(embed.build()).queue(msg -> {
                            if (msg != null) {
                                perfMessageId = msg.getId();
                                persistMessageId(perfMessageId);
                            }
                        });
                    });
        } else {
            channel.sendMessageEmbeds(embed.build()).queue(msg -> {
                if (msg != null) {
                    perfMessageId = msg.getId();
                    persistMessageId(perfMessageId);
                }
            });
        }
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "performance_data.yml");
    }

    private void persistMessageId(String messageId) {
        try {
            File f = dataFile();
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            cfg.set("performance-message-id", messageId);
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save performance message ID: " + e.getMessage(), e);
        }
    }

    private String loadMessageId() {
        try {
            File f = dataFile();
            if (!f.exists()) {
                return null;
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            return cfg.getString("performance-message-id", null);
        } catch (Exception e) {
            return null;
        }
    }

    private static double[] toDoubleArray(Deque<Double> deque) {
        double[] out = new double[deque.size()];
        int i = 0;
        for (Double d : deque) {
            out[i++] = d;
        }
        return out;
    }

    private static double[] toIntArray(Deque<Integer> deque) {
        double[] out = new double[deque.size()];
        int i = 0;
        for (Integer v : deque) {
            out[i++] = v;
        }
        return out;
    }


    private String buildSparkline(double[] values, double max) {
        if (values.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(values.length);
        for (double v : values) {
            double ratio = max > 0 ? Math.max(0.0, Math.min(1.0, v / max)) : 0.0;
            int idx = (int) Math.round(ratio * (SPARK_BLOCKS.length() - 1));
            sb.append(SPARK_BLOCKS.charAt(idx));
        }
        return sb.toString();
    }

    public void reload() {
        perfMessageId = loadMessageId();
    }

    public void shutdown() {
    }
}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Player statistics leaderboard module.
 * Tracks kills, deaths, playtime and posts leaderboards to Discord.
 * Uses StorageManager for persistent data (YAML or MySQL).
 */
public class LeaderboardModule {

    private final ZDiscord plugin;
    private final Map<UUID, Map<String, Long>> statsCache = new ConcurrentHashMap<>();

    public LeaderboardModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadData();
    }

    public void reload() {
        loadData();
    }

    private void loadData() {
        statsCache.clear();
        statsCache.putAll(plugin.getStorageManager().loadStats());
        plugin.getLogger().info(
                "Loaded stats for " + statsCache.size() + " players from " + plugin.getStorageManager().getTypeName());
    }

    /**
     * Increment a stat for a player.
     */
    public void incrementStat(UUID uuid, String stat) {
        long newValue = statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .merge(stat, 1L, Long::sum);
        plugin.getStorageManager().saveStat(uuid, stat, newValue);
    }

    /**
     * Increment a stat by a specific amount (e.g. playtime in seconds).
     */
    public void incrementStatBy(UUID uuid, String stat, long amount) {
        long newValue = statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .merge(stat, amount, Long::sum);
        plugin.getStorageManager().saveStat(uuid, stat, newValue);
    }

    /**
     * Set a stat value for a player.
     */
    public void setStat(UUID uuid, String stat, long value) {
        statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(stat, value);
        plugin.getStorageManager().saveStat(uuid, stat, value);
    }

    /**
     * Get sorted leaderboard for a stat.
     */
    public List<Map.Entry<UUID, Long>> getLeaderboard(String stat, int limit) {
        return statsCache.entrySet().stream()
                .filter(e -> e.getValue().containsKey(stat))
                .map(e -> Map.entry(e.getKey(), e.getValue().get(stat)))
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Send a leaderboard embed to Discord via slash command.
     */
    public void sendLeaderboard(SlashCommandInteractionEvent event, String stat) {
        stat = stat.toLowerCase();
        int topCount = plugin.getConfigManager().getInt("leaderboard.top-count", 10);

        List<Map.Entry<UUID, Long>> entries = getLeaderboard(stat, topCount);
        if (entries.isEmpty()) {
            event.reply("📊 No data available for **" + stat + "** yet!").setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        String[] medals = { "🥇", "🥈", "🥉" };
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, Long> entry = entries.get(i);
            String playerName = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null)
                playerName = "Unknown";
            String prefix = i < 3 ? medals[i] : "**#" + (i + 1) + "**";
            String value = formatStatValue(stat, entry.getValue());
            sb.append(prefix).append(" ").append(playerName).append(" — ").append(value).append("\n");
        }

        String emoji = stat.equals("kills") ? "⚔️" : stat.equals("deaths") ? "💀" : "⏱️";
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(emoji + " " + capitalizeFirst(stat) + " Leaderboard")
                .setDescription(sb.toString())
                .setColor(Color.decode("#F1C40F"))
                .setFooter("ZDiscord • Leaderboard")
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private String formatStatValue(String stat, long value) {
        if (stat.equals("playtime")) {
            long hours = value / 3600;
            long minutes = (value % 3600) / 60;
            return hours + "h " + minutes + "m";
        }
        return String.valueOf(value);
    }

    private String capitalizeFirst(String s) {
        if (s == null || s.isEmpty())
            return s;
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    public void shutdown() {
        // Data is saved on each operation through StorageManager
    }
}

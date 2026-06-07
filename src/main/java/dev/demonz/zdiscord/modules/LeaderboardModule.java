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
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tracks kills, deaths, and playtime in memory, persisting each
 * update to the configured storage backend.
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
        plugin.getLogger().info("Loaded stats for " + statsCache.size()
                + " players from " + plugin.getStorageManager().getTypeName());
    }

    public void incrementStat(UUID uuid, String stat) {
        long newValue = statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .merge(stat, 1L, Long::sum);
        plugin.getStorageManager().saveStat(uuid, stat, newValue);
    }

    public void incrementStatBy(UUID uuid, String stat, long amount) {
        if (amount == 0) {
            return;
        }
        long newValue = statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .merge(stat, amount, Long::sum);
        plugin.getStorageManager().saveStat(uuid, stat, newValue);
    }

    public void setStat(UUID uuid, String stat, long value) {
        statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .put(stat, value);
        plugin.getStorageManager().saveStat(uuid, stat, value);
    }

    public List<Map.Entry<UUID, Long>> getLeaderboard(String stat, int limit) {
        return statsCache.entrySet().stream()
                .filter(e -> e.getValue().containsKey(stat))
                .map(e -> Map.entry(e.getKey(), e.getValue().get(stat)))
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public void sendLeaderboard(SlashCommandInteractionEvent event, String stat) {
        String key = stat.toLowerCase(Locale.ROOT);
        int topCount = plugin.getConfigManager().getInt("leaderboard.top-count", 10);

        List<Map.Entry<UUID, Long>> entries = getLeaderboard(key, topCount);
        if (entries.isEmpty()) {
            event.reply("No data available for **" + key + "** yet.")
                    .setEphemeral(true).queue();
            return;
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            Map.Entry<UUID, Long> entry = entries.get(i);
            String playerName = plugin.getServer().getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) {
                playerName = "Unknown";
            }
            String medal = i == 0 ? "1." : i == 1 ? "2." : i == 2 ? "3." : String.valueOf(i + 1) + ".";
            sb.append("**").append(medal).append("** ")
                    .append(playerName).append(" - ")
                    .append(formatStatValue(key, entry.getValue())).append('\n');
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(capitalize(key) + " Leaderboard")
                .setDescription(sb.toString())
                .setColor(ColorUtil.parseHex("#F1C40F"))
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }

    private String formatStatValue(String stat, long value) {
        if ("playtime".equals(stat)) {
            long hours = value / 3600L;
            long minutes = (value % 3600L) / 60L;
            return hours + "h " + minutes + "m";
        }
        return String.valueOf(value);
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    public void shutdown() {
    }
}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.events.ZDiscordStatUpdateEvent;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import dev.demonz.zdiscord.util.ZLogger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.LayoutComponent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class LeaderboardModule {


    public static final String BTN_PREV_PREFIX = "zdiscord_lb_prev:";
    public static final String BTN_NEXT_PREFIX = "zdiscord_lb_next:";
    public static final String BTN_REFRESH_PREFIX = "zdiscord_lb_refresh:";
    public static final String STAT_SELECT_ID = "zdiscord_lb_stat_select";

    private static final String[] MEDALS = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"};
    private static final int BAR_LENGTH = 10;
    private static final String BAR_FILLED = "\u2588";
    private static final String BAR_EMPTY = "\u2591";

    private final ZDiscord plugin;
    private final Map<UUID, Map<String, Long>> statsCache = new ConcurrentHashMap<>();
    private String panelMessageId;
    private volatile boolean running = true;
    private int perPage;

    public LeaderboardModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void shutdown() {
        running = false;

    }

    public void init() {
        perPage = plugin.getConfigManager().getInt("leaderboard.per-page", 10);
        loadData();
        initPanel();
    }

    public void reload() {
        perPage = plugin.getConfigManager().getInt("leaderboard.per-page", 10);
        loadData();
    }

    private void loadData() {
        statsCache.clear();
        statsCache.putAll(plugin.getStorageManager().loadStats());
        ZLogger.debug(ZLogger.Category.MODULES,
                () -> "Loaded stats for " + statsCache.size() + " players");
    }



    private void initPanel() {
        String channelId = plugin.getConfigManager().getString("leaderboard.panel-channel", "");
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
            return;
        }
        panelMessageId = loadPanelMessageId();
        int interval = plugin.getConfigManager().getInt(
                "leaderboard.panel-update-interval", 120);
        long ticks = interval * 20L;
        plugin.getPlatformAdapter().runAsyncTimer(this::updatePanel, 200L, ticks);
        ZLogger.info(ZLogger.Category.MODULES,
                "Leaderboard panel enabled (updates every " + interval + "s)");
    }

    private void updatePanel() {
        if (!running) return;
        TextChannel channel = plugin.getBotManager()
                .getTextChannel("leaderboard.panel-channel");
        if (channel == null) {
            return;
        }

        List<net.dv8tion.jda.api.entities.MessageEmbed> embeds = new ArrayList<>();
        List<String> stats = plugin.getConfigManager()
                .getStringList("leaderboard.panel-stats");
        if (stats.isEmpty()) {
            stats = List.of("kills", "deaths", "playtime");
        }
        for (String stat : stats) {
            embeds.add(buildLeaderboardEmbed(stat.toLowerCase(), 0, perPage, null).build());
        }

        embeds.add(buildFollowerLeaderboardEmbed().build());

        if (embeds.size() > 10) {
            embeds = embeds.subList(0, 10);
        }

        List<net.dv8tion.jda.api.entities.MessageEmbed> finalEmbeds = embeds;
        if (panelMessageId != null && !panelMessageId.isEmpty()) {
            channel.editMessageEmbedsById(panelMessageId, finalEmbeds).queue(
                    success -> { },
                    error -> {
                        panelMessageId = null;
                        sendNewPanel(channel, finalEmbeds);
                    });
        } else {
            sendNewPanel(channel, finalEmbeds);
        }
    }

    private void sendNewPanel(TextChannel channel,
                              List<net.dv8tion.jda.api.entities.MessageEmbed> embeds) {
        channel.sendMessageEmbeds(embeds).queue(
                msg -> {
                    panelMessageId = msg.getId();
                    persistPanelMessageId(panelMessageId);
                },
                err -> ZLogger.debug(ZLogger.Category.MODULES,
                        "Failed to send leaderboard panel: " + err.getMessage()));
    }



    public void incrementStat(UUID uuid, String stat) {
        incrementStatBy(uuid, stat, 1L);
    }

    public void incrementStatBy(UUID uuid, String stat, long amount) {
        if (amount == 0) {
            return;
        }
        long[] oldRef = new long[1];
        long newValue = statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .compute(stat, (k, v) -> {
                    oldRef[0] = v == null ? 0L : v;
                    return oldRef[0] + amount;
                });
        ZDiscordStatUpdateEvent event = new ZDiscordStatUpdateEvent(
                uuid, stat, oldRef[0], newValue, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                    .put(stat, oldRef[0]);
            return;
        }
        plugin.getStorageManager().saveStat(uuid, stat, newValue);
    }

    public void setStat(UUID uuid, String stat, long value) {
        long[] oldRef = new long[1];
        statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                .compute(stat, (k, v) -> {
                    oldRef[0] = v == null ? 0L : v;
                    return value;
                });
        ZDiscordStatUpdateEvent event = new ZDiscordStatUpdateEvent(
                uuid, stat, oldRef[0], value, !Bukkit.isPrimaryThread());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            statsCache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
                    .put(stat, oldRef[0]);
            return;
        }
        plugin.getStorageManager().saveStat(uuid, stat, value);
    }

    public long getStat(UUID uuid, String stat) {
        var map = statsCache.get(uuid);
        if (map == null) {
            return 0L;
        }
        Long v = map.get(stat);
        return v == null ? 0L : v;
    }

    private static class CachedLeaderboard {
        final long timestamp;
        final List<Map.Entry<UUID, Long>> list;
        CachedLeaderboard(List<Map.Entry<UUID, Long>> list) {
            this.timestamp = System.currentTimeMillis();
            this.list = list;
        }
    }
    private final Map<String, CachedLeaderboard> sortedCache = new ConcurrentHashMap<>();

    public List<Map.Entry<UUID, Long>> getLeaderboard(String stat, int limit) {
        long now = System.currentTimeMillis();
        CachedLeaderboard cached = sortedCache.get(stat);
        List<Map.Entry<UUID, Long>> list;
        if (cached != null && (now - cached.timestamp) < 30_000L) {
            list = cached.list;
        } else {
            list = statsCache.entrySet().stream()
                    .filter(e -> e.getValue().containsKey(stat))
                    .map(e -> Map.entry(e.getKey(), e.getValue().get(stat)))
                    .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                    .collect(Collectors.toList());
            sortedCache.put(stat, new CachedLeaderboard(list));
        }
        return list.stream().limit(limit).collect(Collectors.toList());
    }

    public int getTotalTrackedPlayers() {
        return statsCache.size();
    }

    public int getPlayerRank(UUID uuid, String stat) {
        List<Map.Entry<UUID, Long>> all = getLeaderboard(stat, Integer.MAX_VALUE);
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getKey().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }




    public void sendLeaderboard(SlashCommandInteractionEvent event, String stat) {
        String key = stat.toLowerCase(Locale.ROOT);
        EmbedBuilder embed = buildLeaderboardEmbed(key, 0, perPage, event.getUser().getName());
        List<LayoutComponent> components = buildLeaderboardComponents(key, 0, getTotalPages(key));
        event.replyEmbeds(embed.build()).addComponents(components).queue();
    }




    public boolean handleButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id.startsWith(BTN_PREV_PREFIX) || id.startsWith(BTN_NEXT_PREFIX)) {
            String[] parts = id.split(":");
            if (parts.length < 3) return false;
            String stat = parts[1];
            int page = Integer.parseInt(parts[2]);
            EmbedBuilder embed = buildLeaderboardEmbed(stat, page, perPage,
                    event.getUser().getName());
            List<LayoutComponent> components = buildLeaderboardComponents(
                    stat, page, getTotalPages(stat));
            event.editMessageEmbeds(embed.build()).setComponents(components).queue();
            return true;
        }
        if (id.startsWith(BTN_REFRESH_PREFIX)) {
            String stat = id.substring(BTN_REFRESH_PREFIX.length());
            EmbedBuilder embed = buildLeaderboardEmbed(stat, 0, perPage,
                    event.getUser().getName());
            List<LayoutComponent> components = buildLeaderboardComponents(
                    stat, 0, getTotalPages(stat));
            event.editMessageEmbeds(embed.build()).setComponents(components).queue();
            return true;
        }
        return false;
    }


    public boolean handleSelectInteraction(StringSelectInteractionEvent event) {
        if (!STAT_SELECT_ID.equals(event.getComponentId())) {
            return false;
        }
        String stat = event.getValues().get(0);
        EmbedBuilder embed = buildLeaderboardEmbed(stat, 0, perPage,
                event.getUser().getName());
        List<LayoutComponent> components = buildLeaderboardComponents(
                stat, 0, getTotalPages(stat));
        event.editMessageEmbeds(embed.build()).setComponents(components).queue();
        return true;
    }




    public EmbedBuilder buildLeaderboardEmbed(String stat, int page,
                                               int pageSize, String requesterTag) {
        String key = stat.toLowerCase(Locale.ROOT);
        List<Map.Entry<UUID, Long>> all = getLeaderboard(key, Integer.MAX_VALUE);
        int totalPages = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = safePage * pageSize;
        int to = Math.min(from + pageSize, all.size());

        String emoji = statEmoji(key);
        Color color = statColor(key);
        String title = emoji + " " + capitalize(key) + " Leaderboard";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setColor(color)
                .setTimestamp(Instant.now());

        if (all.isEmpty()) {
            embed.setDescription("No data available for **" + key + "** yet.\n"
                    + "Play on the server to start tracking stats!");
            return embed;
        }


        UUID topPlayer = all.get(0).getKey();
        embed.setThumbnail(HeadUtil.avatar(topPlayer, HeadUtil.SIZE_MEDIUM));

        long maxValue = all.get(0).getValue();

        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) {
            Map.Entry<UUID, Long> entry = all.get(i);
            String playerName = plugin.getServer()
                    .getOfflinePlayer(entry.getKey()).getName();
            if (playerName == null) {
                playerName = "Unknown";
            }


            String rank;
            if (i < 3) {
                rank = MEDALS[i];
            } else {
                rank = "**" + (i + 1) + ".**";
            }


            String bar = buildProgressBar(entry.getValue(), maxValue);
            String value = formatStatValue(key, entry.getValue());

            sb.append(rank).append(" **").append(playerName).append("**")
                    .append("  —  `").append(value).append("`\n")
                    .append("\u200B \u200B \u200B \u200B ").append(bar).append("\n");
        }

        embed.setDescription(sb.toString());


        String footer = "Page " + (safePage + 1) + "/" + totalPages
                + " \u2022 " + all.size() + " players tracked";
        if (requesterTag != null) {
            footer += " \u2022 " + requesterTag;
        }
        embed.setFooter(footer + " \u2022 ZDiscord");

        return embed;
    }


    public List<LayoutComponent> buildLeaderboardComponents(String stat,
                                                             int currentPage,
                                                             int totalPages) {
        List<LayoutComponent> rows = new ArrayList<>();


        boolean hasPrev = currentPage > 0;
        boolean hasNext = currentPage < totalPages - 1;
        rows.add(ActionRow.of(
                Button.secondary(BTN_PREV_PREFIX + stat + ":" + (currentPage - 1),
                                "\u25C0\uFE0F Previous")
                        .withDisabled(!hasPrev),
                Button.primary(BTN_REFRESH_PREFIX + stat,
                        "\uD83D\uDD04 Refresh"),
                Button.secondary(BTN_NEXT_PREFIX + stat + ":" + (currentPage + 1),
                                "Next \u25B6\uFE0F")
                        .withDisabled(!hasNext)
        ));


        StringSelectMenu.Builder menu = StringSelectMenu.create(STAT_SELECT_ID)
                .setPlaceholder("Switch stat category")
                .setMinValues(1)
                .setMaxValues(1)
                .addOption("\u2694\uFE0F Kills", "kills", "Player vs player kills")
                .addOption("\uD83D\uDC80 Deaths", "deaths", "Total player deaths")
                .addOption("\u23F1\uFE0F Playtime", "playtime", "Total time played");
        rows.add(ActionRow.of(menu.build()));

        return rows;
    }




    public EmbedBuilder buildFollowerLeaderboardEmbed() {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83D\uDC65 Most Followed Players")
                .setColor(ColorUtil.parseHex("#9B59B6"))
                .setTimestamp(Instant.now());

        FollowModule fm = plugin.getFollowModule();
        if (fm == null) {
            embed.setDescription("Follow system is disabled.");
            return embed;
        }


        List<Map.Entry<UUID, Integer>> ranked = new ArrayList<>();
        for (UUID uuid : statsCache.keySet()) {
            int count = fm.getFollowerCount(uuid);
            if (count > 0) {
                ranked.add(Map.entry(uuid, count));
            }
        }
        ranked.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));

        if (ranked.isEmpty()) {
            embed.setDescription("No players have followers yet.\n"
                    + "Use `/profile` and hit the **Follow** button!");
            return embed;
        }

        int limit = Math.min(10, ranked.size());
        int maxVal = ranked.get(0).getValue();
        embed.setThumbnail(HeadUtil.avatar(ranked.get(0).getKey(), HeadUtil.SIZE_MEDIUM));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < limit; i++) {
            var entry = ranked.get(i);
            String name = plugin.getServer()
                    .getOfflinePlayer(entry.getKey()).getName();
            if (name == null) name = "Unknown";

            String rank = i < 3 ? MEDALS[i] : "**" + (i + 1) + ".**";
            String bar = buildProgressBar(entry.getValue(), maxVal);
            sb.append(rank).append(" **").append(name).append("**")
                    .append("  —  `").append(entry.getValue()).append(" followers`\n")
                    .append("\u200B \u200B \u200B \u200B ").append(bar).append("\n");
        }
        embed.setDescription(sb.toString());
        embed.setFooter(ranked.size() + " players with followers \u2022 ZDiscord");
        return embed;
    }



    private int getTotalPages(String stat) {
        List<Map.Entry<UUID, Long>> all = getLeaderboard(stat, Integer.MAX_VALUE);
        return Math.max(1, (int) Math.ceil(all.size() / (double) perPage));
    }

    private String buildProgressBar(long value, long maxValue) {
        if (maxValue <= 0) {
            return "`" + BAR_EMPTY.repeat(BAR_LENGTH) + "`";
        }
        int filled = (int) Math.round((value / (double) maxValue) * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        return "`" + BAR_FILLED.repeat(filled) + BAR_EMPTY.repeat(BAR_LENGTH - filled) + "`";
    }

    private String formatStatValue(String stat, long value) {
        if ("playtime".equals(stat)) {
            long hours = value / 3600L;
            long minutes = (value % 3600L) / 60L;
            return hours + "h " + minutes + "m";
        }
        return String.valueOf(value);
    }

    private static String statEmoji(String stat) {
        return switch (stat) {
            case "kills" -> "\u2694\uFE0F";
            case "deaths" -> "\uD83D\uDC80";
            case "playtime" -> "\u23F1\uFE0F";
            case "followers" -> "\uD83D\uDC65";
            default -> "\uD83D\uDCCA";
        };
    }

    private static Color statColor(String stat) {
        return switch (stat) {
            case "kills" -> new Color(0xE74C3C);
            case "deaths" -> new Color(0x95A5A6);
            case "playtime" -> new Color(0x3498DB);
            case "followers" -> new Color(0x9B59B6);
            default -> new Color(0xF1C40F);
        };
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }



    private File panelDataFile() {
        return new File(plugin.getDataFolder(), "leaderboard_panel.yml");
    }

    private void persistPanelMessageId(String messageId) {
        try {
            File f = panelDataFile();
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            cfg.set("panel-message-id", messageId);
            cfg.save(f);
        } catch (IOException e) {
            ZLogger.warn(ZLogger.Category.MODULES,
                    "Failed to save leaderboard panel ID: " + e.getMessage());
        }
    }

    private String loadPanelMessageId() {
        File f = panelDataFile();
        if (!f.exists()) {
            return null;
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
        return cfg.getString("panel-message-id", null);
    }

}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HalloweenEasterEgg;
import dev.demonz.zdiscord.util.HalloweenWindow;
import dev.demonz.zdiscord.util.HeadUtil;
import dev.demonz.zdiscord.util.SkinUtil;
import dev.demonz.zdiscord.util.ZLogger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Seasonal server event: opens on a configurable date window, tallies mob kills
 * in the existing leaderboard storage under {@link #TALLY_STAT}, and crowns a
 * winner once the window closes.
 */
public class HalloweenModule {

    public static final String TALLY_STAT = "halloween_kills";
    private static final String WINDOW_MARKER_KEY = "halloween.window-start";

    private static final String[] MEDALS = {"\uD83E\uDD47", "\uD83E\uDD48", "\uD83E\uDD49"};
    private static final int BAR_LENGTH = 10;
    private static final String BAR_FILLED = "\u2588";
    private static final String BAR_EMPTY = "\u2591";
    private static final String DEFAULT_COLOR = "#E67E22";

    private final ZDiscord plugin;
    private volatile HalloweenEasterEgg engine;
    private volatile boolean active;
    private volatile boolean panelRunning;
    private volatile String panelMessageId;
    private volatile String lastCountdownKey;

    public HalloweenModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        HalloweenWindow window = HalloweenWindow.parse(
                plugin.getConfigManager().getString("halloween.window-start", "10-31"),
                plugin.getConfigManager().getString("halloween.window-end", "10-31"));

        engine = new HalloweenEasterEgg("ZDiscord",
                message -> ZLogger.info(ZLogger.Category.EVENTS, message),
                this::logWindowActive, window, new Phases());
        engine.start();
        active = engine.isWindowActive();
        if (active) {
            beginWindowIfNew(window.windowStartOn(LocalDate.now()));
            startLivePanel();
        }
        ZLogger.info(ZLogger.Category.MODULES, "Halloween event enabled ("
                + window.start() + " to " + window.end() + ").");
    }

    public void reload() {
        shutdown();
        if (plugin.getConfigManager().getBoolean("halloween.enabled", true)) {
            init();
        }
    }

    public void shutdown() {
        HalloweenEasterEgg current = engine;
        engine = null;
        active = false;
        panelRunning = false;
        lastCountdownKey = null;
        if (current != null) current.close();
    }

    public boolean isActive() {
        return active && engine != null && engine.isWindowActive();
    }

    public long daysUntilStart() {
        HalloweenEasterEgg current = engine;
        return current == null ? -1L : current.daysUntilStart();
    }

/**
 * Tallies a kill toward the event. Player kills are excluded so the standings
 * reflect mob hunting rather than a PvP grind. Bosses count for
 * {@code halloween.boss-multiplier} so a wither is worth chasing.
 */
public void recordKill(Player victim) {
        if (!isActive() || victim == null) return;
        if (plugin.getLeaderboardModule() == null) return;
        Player killer = victim.getKiller();
        if (killer == null || killedByPlayer(victim)) return;

        int multiplier = Math.max(1,
                plugin.getConfigManager().getInt("halloween.boss-multiplier", 5));
        long gained = killer.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof org.bukkit.entity.LivingEntity damager
                && isBoss(damager.getType().getKey().getKey())
                ? multiplier : 1;

        plugin.getLeaderboardModule()
                .incrementStatBy(killer.getUniqueId(), TALLY_STAT, gained);
        long total = plugin.getLeaderboardModule().getStat(killer.getUniqueId(), TALLY_STAT);
        if (gained == 1) {
            announceMilestone(killer, total);
        }
    }

    private static final Set<String> BOSS_KEYS = Set.of(
            "ender_dragon", "wither", "elder_guardian");

    private static boolean isBoss(String entityKey) {
        return BOSS_KEYS.contains(entityKey);
    }

    private static boolean killedByPlayer(Player victim) {
        return victim.getLastDamageCause() instanceof EntityDamageByEntityEvent byEntity
                && byEntity.getDamager() instanceof Player;
    }

    /**
     * Zeroes the tally for every player who has a non-zero score so a new
     * window starts from a clean board.
     */
    public void resetTally() {
        LeaderboardModule leaderboard = plugin.getLeaderboardModule();
        if (leaderboard == null) return;
        for (Map.Entry<UUID, Long> entry
                : leaderboard.getLeaderboard(TALLY_STAT, Integer.MAX_VALUE)) {
            if (entry.getValue() <= 0) break;
            leaderboard.setStat(entry.getKey(), TALLY_STAT, 0L);
        }
    }

    /**
     * Clears the tally only when the window has genuinely rolled over, so a
     * server restart mid-event keeps the standings intact.
     */
    private void beginWindowIfNew(LocalDate windowStart) {
        if (windowStart == null) return;
        String marker = windowStart.toString();
        String stored = plugin.getStorageManager().getData(WINDOW_MARKER_KEY, "");
        if (marker.equals(stored)) return;
        resetTally();
        plugin.getStorageManager().setData(WINDOW_MARKER_KEY, marker);
    }

    public void announceOpen(LocalDate windowStart) {
        HalloweenEasterEgg current = engine;
        LocalDate lastDay = windowStart == null || current == null
                ? LocalDate.now()
                : current.getWindow().endIn(windowStart.getYear());
        send(buildAnnounceEmbed(
                windowStart == null ? "today" : windowStart.toString(), lastDay.toString()));
    }

    public void announceFinale() {
        List<Map.Entry<UUID, Long>> winners = standings(3);
        if (winners.isEmpty()) {
            if (plugin.getConfigManager().getBoolean("halloween.announce-finale", true)) {
                send(buildFinaleEmbed(winners));
            }
            broadcast(plugin.getMessageManager().get("halloween-in-game-finale"));
            return;
        }

        for (Map.Entry<UUID, Long> winner : winners) {
            runRewards("halloween.rewards.winner",
                    plugin.getServer().getOfflinePlayer(winner.getKey()));
        }
        for (Map.Entry<UUID, Long> ranked : standings(Integer.MAX_VALUE)) {
            runRewards("halloween.rewards.participant",
                    plugin.getServer().getOfflinePlayer(ranked.getKey()));
        }

        if (plugin.getConfigManager().getBoolean("halloween.announce-finale", true)) {
            send(buildFinaleEmbed(winners));
        }
        broadcast(plugin.getMessageManager().get("halloween-in-game-finale"));
    }

    public void announceStandings() {
        send(buildStandingsEmbed());
    }

    /**
     * Posts the pre-event countdown once per day. Players are only bothered
     * during the final {@code countdown-days} days so a long-running window
     * does not announce itself every morning for a month.
     */
    private void announceCountdown(int daysRemaining, LocalDate windowStart) {
        if (daysRemaining <= 0) return;
        if (!plugin.getConfigManager().getBoolean("halloween.countdown.enabled", true)) return;
        int limit = Math.max(1, plugin.getConfigManager().getInt("halloween.countdown.days", 7));
        if (daysRemaining > limit) return;

        String dayKey = LocalDate.now() + "@" + daysRemaining;
        if (dayKey.equals(lastCountdownKey)) return;
        lastCountdownKey = dayKey;

        send(buildCountdownEmbed(daysRemaining, windowStart));
        broadcast(plugin.getMessageManager().get("halloween-in-game-countdown",
                "%days%", String.valueOf(daysRemaining)));
    }

    private EmbedBuilder buildCountdownEmbed(int daysRemaining, LocalDate windowStart) {
        String plural = daysRemaining == 1 ? "day" : "days";
        return new EmbedBuilder()
                .setTitle("\uD83C\uDF83 The Halloween Hunt Nears")
                .setDescription(daysRemaining == 1
                        ? "The gates open **tomorrow**. Sharpen your sword."
                        : "The gates open in **" + daysRemaining + " " + plural + "** on "
                                + "**" + windowStart + "**.")
                .setColor(color())
                .setTimestamp(Instant.now())
                .setFooter("\uD83D\uDD75 +" + milestonePreview()
                        + " first place \u2022 ZDiscord");
    }

    /**
     * Highest configured milestone, shown on the countdown so players can see
     * there is something to chase.
     */
    private long milestonePreview() {
        return milestones().stream().mapToLong(Long::longValue).max().orElse(0L);
    }

    private List<Long> milestones() {
        return plugin.getConfigManager().getStringList("halloween.milestones").stream()
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        ZLogger.warn(ZLogger.Category.MODULES,
                                "Ignoring non-numeric halloween milestone: " + value);
                        return java.util.stream.Stream.empty();
                    }
                })
                .filter(value -> value > 0)
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * Announces the first time a player crosses each configured threshold, so
     * the channel keeps reacting during the event instead of staying silent.
     */
    private void announceMilestone(Player killer, long total) {
        if (milestones().isEmpty()) return;
        long crossed = milestones().stream()
                .filter(value -> total >= value && total - value < 1)
                .findFirst()
                .orElse(0L);
        if (crossed <= 0) return;

        long pending = milestones().stream().filter(value -> value > total).findFirst().orElse(0L);
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83D\uDD25 Milestone Reached")
                .setDescription("**" + killer.getName() + "** has downed **"
                        + total + "** mobs this hunt.")
                .setColor(color())
                .setTimestamp(Instant.now());
        if (pending > 0) {
            embed.setFooter((pending - total) + " more until the next milestone \u2022 ZDiscord");
        } else {
            embed.setFooter("Every milestone cleared \u2022 ZDiscord");
        }
        send(embed);
        broadcast(plugin.getMessageManager().get("halloween-in-game-milestone",
                "%player%", killer.getName(), "%kills%", String.valueOf(total)));
    }

    /**
     * Runs the configured console commands for a player, mirroring how account
     * linking dispatches its rewards.
     */
    private void runRewards(String configPath, OfflinePlayer target) {
        List<String> rewards = plugin.getConfigManager().getStringList(configPath);
        if (rewards.isEmpty() || target == null) return;
        plugin.getPlatformAdapter().runSync(() -> {
            for (String template : rewards) {
                String resolved = template
                        .replace("%player%", target.getName() == null ? "" : target.getName())
                        .replace("%uuid%", target.getUniqueId().toString());
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to run Halloween reward command: "
                            + e.getMessage());
                }
            }
        });
    }

    /**
     * Self-refreshing standings embed shown for the duration of the event.
     */
    private void startLivePanel() {
        if (!plugin.getConfigManager().getBoolean("halloween.live-panel.enabled", true)) return;
        if (panelMessageId == null) panelMessageId = loadPanelMessageId();
        panelRunning = true;
        int interval = Math.max(5, plugin.getConfigManager()
                .getInt("halloween.live-panel.update-interval", 60));
        plugin.getPlatformAdapter().runTimer(this::updateLivePanel, 100L, interval * 20L);
        ZLogger.info(ZLogger.Category.MODULES,
                "Halloween standings panel enabled (updates every " + interval + "s)");
    }

    private void stopLivePanel() {
        panelRunning = false;
    }

    private void updateLivePanel() {
        if (!panelRunning || !isActive()) return;
        TextChannel channel = resolveChannel();
        if (channel == null) return;

        EmbedBuilder embed = buildStandingsEmbed();
        if (panelMessageId != null && !panelMessageId.isEmpty()) {
            channel.editMessageEmbedsById(panelMessageId, embed.build()).queue(
                    success -> { },
                    error -> {
                        panelMessageId = null;
                        sendNewPanel(channel, embed);
                    });
        } else {
            sendNewPanel(channel, embed);
        }
    }

    private void sendNewPanel(TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build()).queue(
                msg -> {
                    panelMessageId = msg.getId();
                    persistPanelMessageId(panelMessageId);
                },
                error -> ZLogger.debug(ZLogger.Category.MODULES,
                        "Failed to send Halloween standings panel: " + error.getMessage()));
    }

    private File panelDataFile() {
        return new File(plugin.getDataFolder(), "halloween_panel.yml");
    }

    private void persistPanelMessageId(String messageId) {
        try {
            File file = panelDataFile();
            file.getParentFile().mkdirs();
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            cfg.set("panel-message-id", messageId);
            cfg.save(file);
        } catch (IOException e) {
            ZLogger.warn(ZLogger.Category.MODULES,
                    "Failed to save Halloween panel ID: " + e.getMessage());
        }
    }

    private String loadPanelMessageId() {
        File file = panelDataFile();
        if (!file.exists()) return null;
        return YamlConfiguration.loadConfiguration(file).getString("panel-message-id", null);
    }

    /**
     * Standings with a non-zero score, ordered best first.
     */
    public List<Map.Entry<UUID, Long>> standings(int limit) {
        List<Map.Entry<UUID, Long>> result = new ArrayList<>();
        if (plugin.getLeaderboardModule() == null) return result;
        for (Map.Entry<UUID, Long> entry
                : plugin.getLeaderboardModule().getLeaderboard(TALLY_STAT, Integer.MAX_VALUE)) {
            if (entry.getValue() <= 0) break;
            result.add(entry);
            if (result.size() >= limit) break;
        }
        return result;
    }

    public EmbedBuilder buildStandingsEmbed() {
        int top = Math.max(1, plugin.getConfigManager().getInt("halloween.top-n", 5));
        List<Map.Entry<UUID, Long>> rows = standings(top);
        EmbedBuilder embed = buildRankedEmbed(trophyTitle(), rows,
                rows.isEmpty() ? "No kills recorded yet. Go hunt something!" : null);

        long days = daysUntilStart();
        if (days > 0) {
            embed.setFooter("The hunt opens in " + days
                    + (days == 1 ? " day" : " days") + " \u2022 ZDiscord");
        }
        return embed;
    }

    private EmbedBuilder buildAnnounceEmbed(String start, String end) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83D\uDE83 The Halloween Hunt Begins")
                .setDescription("The gates are open and the hunt is on.\n"
                        + "Every hostile mob you put down between **" + start
                        + "** and **" + end + "** counts toward the crown.")
                .setColor(color())
                .setTimestamp(Instant.now())
                .setFooter("Rewards and rules in chat \u2022 ZDiscord");
        return embed;
    }

    private EmbedBuilder buildFinaleEmbed(List<Map.Entry<UUID, Long>> winners) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83D\uDD75 The Hunt Has Ended")
                .setDescription(winners.isEmpty()
                        ? "The hunt is over. Nobody scored a kill this year - a truly cursed event."
                        : "The lantern goes to **" + displayName(winners.get(0).getKey())
                        + "** with **" + winners.get(0).getValue() + "** kills.\n\n"
                        + "*The leaderboard resets when the next hunt opens.*")
                .setColor(color())
                .setTimestamp(Instant.now())
                .setFooter("Thanks for playing \u2022 ZDiscord");
        return embed;
    }

    private EmbedBuilder buildRankedEmbed(String title, List<Map.Entry<UUID, Long>> rows,
                                          String emptyText) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("\uD83C\uDFB7 " + title)
                .setColor(color())
                .setTimestamp(Instant.now());

        if (rows.isEmpty()) {
            embed.setDescription(emptyText != null ? emptyText : "No kills recorded yet.");
            return embed;
        }

        long maxValue = rows.get(0).getValue();
        embed.setThumbnail(SkinUtil.avatar(plugin, rows.get(0).getKey(),
                displayName(rows.get(0).getKey()), HeadUtil.SIZE_MEDIUM));

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            Map.Entry<UUID, Long> entry = rows.get(i);
            String rank = i < 3 ? MEDALS[i] : "**" + (i + 1) + ".**";
            sb.append(rank).append(" **").append(displayName(entry.getKey())).append("**")
                    .append("  \u2014  `").append(entry.getValue()).append("`\n")
                    .append("\u200B \u200B \u200B \u200B ").append(progressBar(entry.getValue(), maxValue))
                    .append("\n");
        }
        embed.setDescription(sb.toString());
        embed.setFooter(rows.size() + " ranked \u2022 ZDiscord");
        return embed;
    }

    private String trophyTitle() {
        return plugin.getConfigManager()
                .getString("halloween.trophy-title", "Graveyard King");
    }

    private String progressBar(long value, long maxValue) {
        if (maxValue <= 0) return "`" + BAR_EMPTY.repeat(BAR_LENGTH) + "`";
        int filled = (int) Math.round((value / (double) maxValue) * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        return "`" + BAR_FILLED.repeat(filled) + BAR_EMPTY.repeat(BAR_LENGTH - filled) + "`";
    }

    private String displayName(UUID uuid) {
        String name = plugin.getServer().getOfflinePlayer(uuid).getName();
        return name == null ? "Unknown" : name;
    }

    private Color color() {
        return ColorUtil.parseHex(
                plugin.getConfigManager().getString("halloween.color", DEFAULT_COLOR));
    }

    private void send(EmbedBuilder embed) {
        TextChannel channel = resolveChannel();
        if (channel == null) return;
        channel.sendMessageEmbeds(embed.build()).queue(
                success -> { },
                error -> ZLogger.debug(ZLogger.Category.MODULES,
                        "Failed to send Halloween embed: " + error.getMessage()));
    }

    private TextChannel resolveChannel() {
        if (plugin.getBotManager() == null || !plugin.getBotManager().isConnected()) return null;
        TextChannel channel = plugin.getBotManager().getTextChannel("halloween.channel");
        if (channel != null) return channel;
        return plugin.getBotManager().getTextChannel("channels.events");
    }

    /**
 * The scheduler fires the notify callback on every check while the window is
 * open, so it only records a debug line. Player-facing broadcasts happen once
 * on the opening boundary and per joiner via {@link #greetPlayer}.
 */
private void logWindowActive() {
        ZLogger.debug(ZLogger.Category.EVENTS, "Halloween window is active.");
    }

    private void broadcast(String message) {
        plugin.getPlatformAdapter().runSync(() ->
                plugin.getServer().getOnlinePlayers().forEach(player ->
                        plugin.getPlatformAdapter().runForEntity(player,
                                () -> player.sendMessage(message))));
    }

    public void greetPlayer(Player player) {
        if (!isActive()) return;
        if (!plugin.getConfigManager().getBoolean("halloween.greet-players", true)) return;
        if (player == null || !player.isOnline()) return;
        HalloweenEasterEgg current = engine;
        if (current == null) return;
        current.greetPlayer(player.getUniqueId(), message -> player.sendMessage(
                plugin.getMessageManager().get("halloween-in-game-greeting")));
    }

    private final class Phases implements HalloweenEasterEgg.PhaseListener {
        @Override
        public void onCountdown(int daysRemaining, LocalDate windowStart) {
            announceCountdown(daysRemaining, windowStart);
        }

        @Override
        public void onWindowOpen(LocalDate windowStart) {
            active = true;
            beginWindowIfNew(windowStart);
            announceOpen(windowStart);
            startLivePanel();
            broadcast(plugin.getMessageManager().get("halloween-in-game-open"));
        }

        @Override
        public void onFinale(LocalDate windowStart, LocalDate windowEnd) {
            active = false;
            announceFinale();
            stopLivePanel();
        }
    }
}
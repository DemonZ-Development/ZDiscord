package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HalloweenEasterEgg;
import dev.demonz.zdiscord.util.HalloweenHuntRules;
import dev.demonz.zdiscord.util.HalloweenWindow;
import dev.demonz.zdiscord.util.HeadUtil;
import dev.demonz.zdiscord.util.SkinUtil;
import dev.demonz.zdiscord.util.ZLogger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HalloweenModule {

    private static final Color DEFAULT_COLOR = new Color(0xE67E22);
    private static final int MAX_ROWS = 25;
    private static final int MAX_DESCRIPTION = 3500;
    private static final int BAR_LENGTH = 10;
    private static final Map<String, CompletableFuture<String>> PANEL_CREATIONS = new ConcurrentHashMap<>();

    private final ZDiscord plugin;
    private final HalloweenHunt hunt;
    private final NamespacedKey spawnerKey;
    private final Map<UUID, LocalDate> greetedWindows = new ConcurrentHashMap<>();
    private volatile HalloweenEasterEgg engine;
    private volatile HalloweenHuntRules rules;
    private volatile List<Long> milestones = List.of();
    private volatile ZoneId zone = ZoneId.systemDefault();
    private volatile Color eventColor = DEFAULT_COLOR;
    private volatile long generation;
    private volatile boolean active;
    private volatile boolean panelRunning;
    private volatile boolean panelPending;
    private boolean finalPanelRequested;
    private String panelMessageId;
    private String panelChannelId;
    private boolean cosmeticsEnabled;
    private boolean joinTitle;
    private boolean milestoneEffects;
    private Sound sound = Sound.AMBIENT_CAVE;
    private Particle particle = Particle.SOUL;
    private float volume = 0.4f;
    private float pitch = 0.8f;
    private int particleCount = 12;

    public HalloweenModule(ZDiscord plugin) {
        this(plugin, new HalloweenHunt(plugin.getStorageManager()));
    }

    public HalloweenModule(ZDiscord plugin, HalloweenHunt hunt) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.hunt = Objects.requireNonNull(hunt, "hunt");
        spawnerKey = new NamespacedKey(plugin, "halloween-spawner-mob");
    }

    public synchronized void init() {
        shutdown();
        if (!plugin.getConfigManager().getBoolean("halloween.enabled", true)) return;
        loadSettings();
        HalloweenWindow window = HalloweenWindow.parse(
                plugin.getConfigManager().getString("halloween.window-start", "10-31"),
                plugin.getConfigManager().getString("halloween.window-end", "10-31"));
        long run = generation;
        engine = new HalloweenEasterEgg("ZDiscord",
                message -> ZLogger.info(ZLogger.Category.EVENTS, message),
                () -> { }, window, new Phases(run), zone);
        engine.start();
        ZLogger.info(ZLogger.Category.MODULES, "Halloween event enabled ("
                + window.start() + " to " + window.end() + ", " + zone + ").");
    }

    public void reload() {
        init();
    }

    public synchronized void shutdown() {
        HalloweenEasterEgg previous;
        synchronized (hunt) {
            generation++;
            previous = engine;
            engine = null;
            active = false;
            panelRunning = false;
            panelPending = false;
            finalPanelRequested = false;
            panelMessageId = null;
            panelChannelId = null;
        }
        if (previous != null) previous.close();
    }

    public boolean isActive() {
        HalloweenEasterEgg current = engine;
        return active && current != null && current.isWindowActive();
    }

    public long daysUntilStart() {
        HalloweenEasterEgg current = engine;
        return current == null ? -1L : current.isWindowActive() ? 0L : current.daysUntilStart();
    }

    public LocalDate startDate() {
        HalloweenEasterEgg current = engine;
        if (current == null) return null;
        LocalDate today = LocalDate.now(zone);
        LocalDate started = current.getWindow().windowStartOn(today);
        return started == null ? current.getWindow().nextStartOnOrAfter(today) : started;
    }

    public LocalDate endDate() {
        HalloweenEasterEgg current = engine;
        LocalDate start = startDate();
        return current == null || start == null ? null : current.getWindow().endIn(start.getYear());
    }

    public long score(UUID playerId) {
        return hunt.score(playerId);
    }

    public List<Map.Entry<UUID, Long>> standings(int limit) {
        return hunt.standings(limit);
    }

    public String displayName(UUID playerId) {
        String name = plugin.getServer().getOfflinePlayer(playerId).getName();
        return name == null ? "Unknown" : name;
    }

    public void recordKill(LivingEntity victim) {
        long run = generation;
        if (!current(run) || !isActive() || victim == null || victim instanceof Player) return;
        Player killer = victim.getKiller();
        HalloweenHuntRules currentRules = rules;
        if (killer == null || currentRules == null) return;
        var type = victim.getType();
        boolean hostile = victim instanceof Enemy;
        String world = victim.getWorld().getName();
        boolean fromSpawner = victim.getPersistentDataContainer().has(spawnerKey, PersistentDataType.BYTE);
        plugin.getPlatformAdapter().runForEntity(killer, () -> {
            if (current(run) && isActive() && killer.isOnline()) {
                long gained = currentRules.pointsFor(type, hostile, killer.getGameMode(), world, fromSpawner);
                recordScore(run, killer, gained);
            }
        });
    }

    private void recordScore(long run, Player killer, long gained) {
        if (gained <= 0L) return;

        long before;
        long after;
        synchronized (hunt) {
            if (!current(run) || !isActive()) return;
            before = hunt.score(killer.getUniqueId());
            after = hunt.addScore(killer.getUniqueId(), gained);
        }
        List<Long> crossed = HalloweenHunt.crossedMilestones(before, after, milestones);
        if (crossed.isEmpty()) return;
        String name = killer.getName();
        onMain(run, () -> {
            for (long milestone : crossed) {
                send(eventEmbed(discord("halloween-discord-milestone-title"),
                        discord("halloween-discord-milestone", "%player%", name,
                                "%points%", Long.toString(after), "%milestone%", Long.toString(milestone))));
                broadcast(plugin.getMessageManager().get("halloween-in-game-milestone",
                        "%player%", name, "%points%", Long.toString(after),
                        "%kills%", Long.toString(after), "%milestone%", Long.toString(milestone)));
            }
            if (cosmeticsEnabled && milestoneEffects) {
                plugin.getPlatformAdapter().runForEntity(killer, () -> {
                    if (current(run) && killer.isOnline()) showEffects(killer);
                });
            }
        });
    }

    @SuppressWarnings("deprecation")
    public void greetPlayer(Player player) {
        long run = generation;
        boolean sendChat = plugin.getConfigManager().getBoolean("halloween.greet-players", true);
        if (!isActive() || player == null || (!sendChat && !cosmeticsEnabled)) return;
        plugin.getPlatformAdapter().runForEntity(player, () -> {
            HalloweenEasterEgg current = engine;
            if (!current(run) || current == null || !isActive() || !player.isOnline()) return;
            LocalDate started = current.getWindow().windowStartOn(LocalDate.now(zone));
            if (started == null || started.equals(greetedWindows.get(player.getUniqueId()))) return;
            current.greetPlayer(player.getUniqueId(), ignored -> {
                if (started.equals(greetedWindows.put(player.getUniqueId(), started))) return;
                if (sendChat) {
                    player.sendMessage(plugin.getMessageManager().get("halloween-in-game-greeting"));
                }
                if (cosmeticsEnabled) {
                    if (joinTitle) {
                        player.sendTitle(plugin.getMessageManager().get("halloween-join-title"),
                                plugin.getMessageManager().get("halloween-join-subtitle"), 10, 50, 15);
                    }
                    showEffects(player);
                }
            });
        });
    }

    public void announceOpen(LocalDate start) {
        HalloweenEasterEgg current = engine;
        if (current == null || start == null) return;
        LocalDate end = current.getWindow().endIn(start.getYear());
        send(eventEmbed(discord("halloween-discord-open-title"),
                discord("halloween-discord-open", "%start%", start.toString(), "%end%", end.toString())));
    }

    public void announceFinale() {
        HalloweenEasterEgg current = engine;
        if (current != null) {
            LocalDate closed = current.getWindow().mostRecentClosedStart(LocalDate.now(zone));
            onMain(generation, () -> finishWindow(closed));
        }
    }

    public void announceStandings() {
        onMain(generation, () -> send(buildStandingsEmbed()));
    }

    public EmbedBuilder buildStandingsEmbed() {
        int top = Math.max(1, Math.min(MAX_ROWS,
                plugin.getConfigManager().getInt("halloween.top-n", 5)));
        List<Map.Entry<UUID, Long>> rows = standings(top);
        LocalDate date = isActive() ? endDate() : startDate();
        String state = isActive()
                ? discord("halloween-discord-active", "%date%", date == null ? "unknown" : date.toString())
                : discord("halloween-discord-upcoming", "%date%", date == null ? "unknown" : date.toString(),
                        "%days%", Long.toString(daysUntilStart()));
        StringBuilder description = new StringBuilder(limit(state, 700)).append("\n\n");
        if (rows.isEmpty()) {
            description.append(discord("halloween-discord-empty"));
        } else {
            long best = rows.get(0).getValue();
            for (int index = 0; index < rows.size(); index++) {
                Map.Entry<UUID, Long> row = rows.get(index);
                String line = discord("halloween-discord-ranking", "%rank%", Integer.toString(index + 1),
                        "%player%", displayName(row.getKey()), "%points%", Long.toString(row.getValue()))
                        + "\n" + progressBar(row.getValue(), best) + "\n";
                if (description.length() + line.length() > MAX_DESCRIPTION) break;
                description.append(line);
            }
        }
        EmbedBuilder embed = eventEmbed(
                plugin.getConfigManager().getString("halloween.trophy-title", "Graveyard King"),
                description.toString());
        if (!rows.isEmpty()) {
            UUID leader = rows.get(0).getKey();
            embed.setThumbnail(SkinUtil.avatar(plugin, leader, displayName(leader), HeadUtil.SIZE_MEDIUM));
        }
        return embed;
    }

    private void finishWindow(LocalDate start) {
        HalloweenEasterEgg current = engine;
        if (current == null) return;
        if (!current.isWindowActive()) {
            active = false;
            finalPanelRequested = panelRunning;
            panelRunning = false;
            if (finalPanelRequested && !panelPending) {
                finalPanelRequested = false;
                updateLivePanel(generation);
            }
        }
        if (!hunt.claimFinale(start)) return;
        List<Map.Entry<UUID, Long>> participants = standings(Integer.MAX_VALUE);
        Map.Entry<UUID, Long> winner = participants.get(0);
        long run = generation;
        runRewards(run, "halloween.rewards.winner", plugin.getServer().getOfflinePlayer(winner.getKey()));
        for (Map.Entry<UUID, Long> participant : participants) {
            if (!current(run)) return;
            runRewards(run, "halloween.rewards.participant",
                    plugin.getServer().getOfflinePlayer(participant.getKey()));
        }
        if (!current(run)) return;
        if (plugin.getConfigManager().getBoolean("halloween.announce-finale", true)) {
            send(eventEmbed(discord("halloween-discord-finale-title"),
                    discord("halloween-discord-finale", "%player%", displayName(winner.getKey()),
                            "%points%", Long.toString(winner.getValue()))));
        }
        broadcast(plugin.getMessageManager().get("halloween-in-game-finale"));
    }

    private void announceCountdown(int days, LocalDate start) {
        if (days <= 0 || !plugin.getConfigManager().getBoolean("halloween.countdown.enabled", true)) return;
        int range = Math.max(0, plugin.getConfigManager().getInt("halloween.countdown.days", 7));
        if (days > range) return;
        String marker = LocalDate.now(zone) + "@" + start;
        if (marker.equals(plugin.getStorageManager().getData("halloween.countdown-marker", ""))) return;
        plugin.getStorageManager().setData("halloween.countdown-marker", marker);
        String unit = days == 1 ? "day" : "days";
        send(eventEmbed(discord("halloween-discord-countdown-title"),
                discord("halloween-discord-countdown", "%days%", Integer.toString(days),
                        "%unit%", unit, "%date%", start.toString())));
        broadcast(plugin.getMessageManager().get("halloween-in-game-countdown",
                "%days%", Integer.toString(days), "%unit%", unit));
    }

    private void runRewards(long run, String configPath, OfflinePlayer player) {
        String name = player.getName();
        for (String template : plugin.getConfigManager().getStringList(configPath)) {
            if (!current(run)) return;
            if (template.isBlank()) continue;
            if (template.contains("%player%") && (name == null || name.isBlank())) {
                ZLogger.warn(ZLogger.Category.EVENTS,
                        "Skipping Halloween reward that requires an unknown player name.");
                continue;
            }
            String command = template.replace("%player%", name == null ? "" : name)
                    .replace("%uuid%", player.getUniqueId().toString());
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
                    ZLogger.warn(ZLogger.Category.EVENTS, "Halloween reward command was not accepted.");
                }
            } catch (RuntimeException failure) {
                ZLogger.warn(ZLogger.Category.EVENTS,
                        "Failed to run Halloween reward command (" + failure.getClass().getSimpleName() + ").");
            }
        }
    }

    private void startLivePanel(long run) {
        if (panelRunning || !plugin.getConfigManager().getBoolean("halloween.live-panel.enabled", true)) return;
        panelRunning = true;
        schedulePanel(run, 100L);
    }

    private void schedulePanel(long run, long delay) {
        plugin.getPlatformAdapter().runLater(() -> {
            if (!current(run) || !panelRunning || !isActive()) return;
            try {
                updateLivePanel(run);
            } catch (RuntimeException failure) {
                panelPending = false;
                ZLogger.debug(ZLogger.Category.MODULES,
                        "Could not update Halloween standings panel: " + failure.getMessage());
            } finally {
                if (current(run) && panelRunning && isActive()) {
                    long interval = Math.max(5, plugin.getConfigManager()
                            .getInt("halloween.live-panel.update-interval", 60));
                    schedulePanel(run, interval * 20L);
                }
            }
        }, Math.max(1L, delay));
    }

    private void updateLivePanel(long run) {
        if (panelPending) return;
        TextChannel channel = resolveChannel();
        if (channel == null) return;
        if (!channel.getId().equals(panelChannelId)) {
            panelChannelId = channel.getId();
            panelMessageId = loadPanelMessageId(panelChannelId);
        }
        String creationKey = panelCreationKey(channel.getId());
        CompletableFuture<String> creating = PANEL_CREATIONS.get(creationKey);
        if (creating != null) {
            if (!creating.isDone()) return;
            if (!creating.isCompletedExceptionally()) {
                panelMessageId = creating.getNow(null);
            }
            PANEL_CREATIONS.remove(creationKey, creating);
        }
        if (panelMessageId == null) {
            panelMessageId = loadPanelMessageId(panelChannelId);
        }
        EmbedBuilder embed = buildStandingsEmbed();
        panelPending = true;
        if (panelMessageId == null || panelMessageId.isEmpty()) {
            CompletableFuture<String> creation = new CompletableFuture<>();
            if (PANEL_CREATIONS.putIfAbsent(creationKey, creation) != null) {
                panelPending = false;
                return;
            }
            String channelId = channel.getId();
            try {
                channel.sendMessageEmbeds(embed.build()).queue(message -> {
                    persistPanelMessageId(channelId, message.getId());
                    creation.complete(message.getId());
                    onMain(run, () -> {
                        if (channelId.equals(panelChannelId)) panelMessageId = message.getId();
                        panelComplete(run);
                    });
                }, failure -> {
                    creation.completeExceptionally(failure);
                    PANEL_CREATIONS.remove(creationKey, creation);
                    panelFailure(run, failure, false);
                });
            } catch (RuntimeException failure) {
                creation.completeExceptionally(failure);
                PANEL_CREATIONS.remove(creationKey, creation);
                throw failure;
            }
        } else {
            channel.editMessageEmbedsById(panelMessageId, embed.build()).queue(
                    message -> onMain(run, () -> panelComplete(run)),
                    failure -> panelFailure(run, failure, true));
        }
    }

    private String panelCreationKey(String channelId) {
        return plugin.getDataFolder().getAbsolutePath() + "/" + channelId;
    }

    private void panelComplete(long run) {
        panelPending = false;
        if (finalPanelRequested) {
            finalPanelRequested = false;
            updateLivePanel(run);
        }
    }

    private void panelFailure(long run, Throwable failure, boolean editing) {
        onMain(run, () -> {
            panelPending = false;
            if (editing && failure instanceof ErrorResponseException response
                    && response.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                panelMessageId = null;
                persistPanelMessageId(panelChannelId, null);
                if (finalPanelRequested) {
                    finalPanelRequested = false;
                    updateLivePanel(run);
                }
            }
            ZLogger.debug(ZLogger.Category.MODULES,
                    "Could not update Halloween standings panel: " + failure.getMessage());
        });
    }

    private File panelDataFile() {
        return new File(plugin.getDataFolder(), "halloween_panel.yml");
    }

    private void persistPanelMessageId(String channelId, String messageId) {
        synchronized (PANEL_CREATIONS) {
            try {
                File file = panelDataFile();
                file.getParentFile().mkdirs();
                YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
                data.set("panel-channel-id", channelId);
                data.set("panel-message-id", messageId);
                data.save(file);
            } catch (IOException failure) {
                ZLogger.warn(ZLogger.Category.MODULES,
                        "Could not save Halloween panel ID: " + failure.getMessage());
            }
        }
    }

    private String loadPanelMessageId(String channelId) {
        synchronized (PANEL_CREATIONS) {
            File file = panelDataFile();
            if (!file.exists()) return null;
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            String savedChannel = data.getString("panel-channel-id", "");
            return savedChannel.isEmpty() || savedChannel.equals(channelId)
                    ? data.getString("panel-message-id", null) : null;
        }
    }

    private EmbedBuilder eventEmbed(String title, String description) {
        return new EmbedBuilder().setAuthor("Halloween Hunt")
                .setTitle(limit(title, 256)).setDescription(limit(description, MAX_DESCRIPTION))
                .setColor(eventColor).setTimestamp(Instant.now())
                .setFooter(limit(discord("halloween-discord-footer"), 512));
    }

    private String discord(String key, String... replacements) {
        return ColorUtil.stripColor(plugin.getMessageManager().get(key, replacements));
    }

    private static String limit(String value, int length) {
        if (value == null || value.length() <= length) return value;
        return value.substring(0, length - 3) + "...";
    }

    private static String progressBar(long value, long maximum) {
        int filled = maximum <= 0L ? 0 : (int) Math.round(value / (double) maximum * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));
        return "`[" + "#".repeat(filled) + "-".repeat(BAR_LENGTH - filled) + "]`";
    }

    private void send(EmbedBuilder embed) {
        TextChannel channel = resolveChannel();
        if (channel == null) return;
        channel.sendMessageEmbeds(embed.build()).queue(ignored -> { },
                failure -> ZLogger.debug(ZLogger.Category.EVENTS,
                        "Could not send Halloween announcement: " + failure.getMessage()));
    }

    private TextChannel resolveChannel() {
        if (plugin.getBotManager() == null || !plugin.getBotManager().isConnected()) return null;
        TextChannel channel = plugin.getBotManager().getTextChannel("halloween.channel");
        return channel == null ? plugin.getBotManager().getTextChannel("channels.events") : channel;
    }

    private void broadcast(String message) {
        if (!plugin.getConfigManager().getBoolean("halloween.broadcasts.enabled", true)) return;
        long run = generation;
        plugin.getServer().getOnlinePlayers().forEach(player ->
                plugin.getPlatformAdapter().runForEntity(player, () -> {
                    if (current(run) && player.isOnline()) player.sendMessage(message);
                }));
    }

    private boolean current(long run) {
        return generation == run && engine != null;
    }

    private void onMain(long run, Runnable task) {
        if (!current(run)) return;
        try {
            plugin.getPlatformAdapter().runSync(() -> {
                if (current(run)) task.run();
            });
        } catch (RuntimeException failure) {
            if (current(run)) throw failure;
        }
    }

    private void loadSettings() {
        var config = plugin.getConfigManager().getConfig();
        rules = new HalloweenHuntRules(config);
        List<Long> configured = new ArrayList<>();
        for (String value : config.getStringList("halloween.milestones")) {
            try {
                long milestone = Long.parseLong(value.trim());
                if (milestone > 0L) configured.add(milestone);
            } catch (NumberFormatException failure) {
                ZLogger.warn(ZLogger.Category.MODULES, "Ignoring invalid Halloween milestone: " + value);
            }
        }
        milestones = configured.stream().distinct().sorted().toList();
        String timezone = config.getString("halloween.timezone", "server").trim();
        try {
            zone = timezone.isEmpty() || timezone.equalsIgnoreCase("server")
                    ? ZoneId.systemDefault() : ZoneId.of(timezone);
        } catch (DateTimeException failure) {
            zone = ZoneId.systemDefault();
            ZLogger.warn(ZLogger.Category.MODULES, "Invalid Halloween timezone; using server timezone.");
        }
        try {
            eventColor = Color.decode(config.getString("halloween.color", "#E67E22"));
        } catch (NumberFormatException failure) {
            eventColor = DEFAULT_COLOR;
            ZLogger.warn(ZLogger.Category.MODULES, "Invalid Halloween color; using #E67E22.");
        }
        cosmeticsEnabled = config.getBoolean("halloween.cosmetics.enabled", false);
        joinTitle = config.getBoolean("halloween.cosmetics.join-title", true);
        milestoneEffects = config.getBoolean("halloween.cosmetics.milestone-effects", true);
        volume = (float) bounded(config.getDouble("halloween.cosmetics.volume", 0.4), 0.0, 1.0, 0.4);
        pitch = (float) bounded(config.getDouble("halloween.cosmetics.pitch", 0.8), 0.5, 2.0, 0.8);
        particleCount = Math.max(0, Math.min(100, config.getInt("halloween.cosmetics.particle-count", 12)));
        try {
            sound = Sound.valueOf(config.getString("halloween.cosmetics.sound", "AMBIENT_CAVE")
                    .trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            sound = Sound.AMBIENT_CAVE;
            ZLogger.warn(ZLogger.Category.MODULES, "Invalid Halloween sound; using AMBIENT_CAVE.");
        }
        try {
            particle = Particle.valueOf(config.getString("halloween.cosmetics.particle", "SOUL")
                    .trim().toUpperCase(Locale.ROOT));
            if (particle.getDataType() != Void.class) {
                throw new IllegalArgumentException("Particle requires extra data");
            }
        } catch (IllegalArgumentException failure) {
            particle = Particle.SOUL;
            ZLogger.warn(ZLogger.Category.MODULES, "Invalid or data-dependent Halloween particle; using SOUL.");
        }
    }

    private static double bounded(double value, double minimum, double maximum, double fallback) {
        return Double.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    private void showEffects(Player player) {
        if (volume > 0.0f) player.playSound(player.getLocation(), sound, volume, pitch);
        if (particleCount > 0) {
            player.spawnParticle(particle, player.getLocation().add(0, 1.0, 0),
                    particleCount, 0.4, 0.5, 0.4, 0.01);
        }
    }

    private final class Phases implements HalloweenEasterEgg.PhaseListener {
        private final long run;

        private Phases(long run) {
            this.run = run;
        }

        @Override
        public void onCountdown(int days, LocalDate start) {
            onMain(run, () -> announceCountdown(days, start));
        }

        @Override
        public void onWindowOpen(LocalDate start) {
            onMain(run, () -> {
                boolean opened = hunt.beginWindow(start);
                if (opened) {
                    greetedWindows.entrySet().removeIf(entry -> !start.equals(entry.getValue()));
                }
                active = true;
                if (opened && plugin.getConfigManager().getBoolean("halloween.announce-open", true)) {
                    announceOpen(start);
                    broadcast(plugin.getMessageManager().get("halloween-in-game-open"));
                }
                startLivePanel(run);
                plugin.getServer().getOnlinePlayers().forEach(HalloweenModule.this::greetPlayer);
            });
        }

        @Override
        public void onFinale(LocalDate start, LocalDate end) {
            onMain(run, () -> finishWindow(start));
        }
    }
}

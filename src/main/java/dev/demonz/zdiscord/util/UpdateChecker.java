package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.minecraft.commands.ZDiscordCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerJoinEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class UpdateChecker implements Listener {

    private static final String PROJECT_SLUG = "zdiscord";
    private static final String API_URL = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String PAGE_URL = "https://modrinth.com/project/" + PROJECT_SLUG;

    private static final long REPEAT_CHECK_TICKS = 20L * 60L * 60L * 5L;
    private static final Pattern VERSION_TOKEN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(.+))?");

    private final ZDiscord plugin;
    private volatile String latestVersion;
    private volatile boolean updateAvailable;
    private volatile boolean running = true;
    private final dev.demonz.zdiscord.platform.PlatformAdapter.TaskHandle timer;

    private final AtomicBoolean discordAnnouncedForCurrent = new AtomicBoolean(false);

    public UpdateChecker(ZDiscord plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        timer = plugin.getPlatformAdapter().scheduleAsyncTimer(this::checkForUpdates, 200L, REPEAT_CHECK_TICKS);
    }

    private void checkForUpdates() {
        if (!running || !plugin.getConfigManager().getBoolean("misc.update-checker", true)) return;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent",
                    "DemonZ-Development/ZDiscord (" + plugin.getDescription().getVersion() + ")");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);

            if (connection.getResponseCode() != 200) {
                return;
            }

            JSONArray versions;
            try (InputStreamReader reader = new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8)) {
                versions = (JSONArray) new JSONParser().parse(reader);
            }
            if (versions.isEmpty()) return;

            JSONObject latest = (JSONObject) versions.get(0);
            if (!running || !plugin.getConfigManager().getBoolean("misc.update-checker", true)) return;
            String previousVersion = latestVersion;
            latestVersion = (String) latest.get("version_number");

            String currentVersion = plugin.getDescription().getVersion();
            updateAvailable = isNewer(latestVersion, currentVersion);
            plugin.debug("Update check: running v" + currentVersion
                    + ", latest is v" + latestVersion);
            if (updateAvailable) {
                if (!java.util.Objects.equals(previousVersion, latestVersion)) discordAnnouncedForCurrent.set(false);
                plugin.getLogger().info("A new version of ZDiscord is available: v"
                        + latestVersion + " (" + PAGE_URL + ")");
            }

            if (updateAvailable
                    && !plugin.getConfigManager().getBoolean("misc.update-silent", false)
                    && discordAnnouncedForCurrent.compareAndSet(false, true)) {
                postSilentDiscordNotice(currentVersion);
            }
        } catch (Exception e) {
            plugin.debug("Update check failed: " + e.getMessage());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void postSilentDiscordNotice(String currentVersion) {
        if (!plugin.getBotManager().isConnected()) {
            discordAnnouncedForCurrent.set(false);
            return;
        }
        String channelId = plugin.getConfigManager().getString("misc.update-channel", "").trim();
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) return;

        TextChannel channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        if (channel == null) {
            plugin.debug("Update-check Discord channel '" + channelId + "' not found.");
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("ZDiscord update available")
                .setDescription("A new version of **ZDiscord** is available. "
                        + "No action is required — this is a quiet notice.\n\n"
                        + "Installed: `v" + currentVersion + "`\n"
                        + "Latest: `v" + latestVersion + "`")
                .setColor(0xF1C40F)
                .addField("Changelog", PAGE_URL + "/changelog", false)
                .addField("Download", PAGE_URL + "/versions", false)
                .setTimestamp(Instant.now())
                .setFooter("Silent update check", null);
        channel.sendMessageEmbeds(embed.build()).queue(
                success -> plugin.debug("Posted silent update notice to " + channelId),
                error -> {
                    discordAnnouncedForCurrent.set(false);
                    plugin.debug("Failed to post silent update notice: " + error.getMessage());
                });
    }

    public static boolean isNewer(String candidate, String current) {
        if (candidate == null || current == null || candidate.equals(current)) return false;

        int[] c = parseVersion(candidate);
        int[] m = parseVersion(current);
        if (c == null || m == null) return false;

        for (int i = 0; i < 3; i++) {
            if (c[i] != m[i]) return c[i] > m[i];
        }

        return preRelease(candidate) == null && preRelease(current) != null;
    }

    private static int[] parseVersion(String version) {
        Matcher m = VERSION_TOKEN.matcher(version);
        if (!m.find()) return null;
        try {
            return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)),
                    m.group(3) != null ? Integer.parseInt(m.group(3)) : 0};
        } catch (NumberFormatException overflow) { return null; }
    }

    private static String preRelease(String version) {
        Matcher m = VERSION_TOKEN.matcher(version);
        if (!m.find()) return null;
        return m.group(4);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!running || !plugin.getConfigManager().getBoolean("misc.update-checker", true)) return;
        if (!updateAvailable) return;
        if (plugin.getConfigManager().getBoolean("misc.update-silent", false)) return;

        Player player = event.getPlayer();
        if (!player.hasPermission("zdiscord.admin")) return;
        if (isDismissedByPlayer(player)) return;
        sendClickableNotification(player);
    }

    private boolean isDismissedByPlayer(Player player) {
        if (plugin.getCommand("zdiscord") == null) return false;
        if (!(plugin.getCommand("zdiscord").getExecutor()
                instanceof ZDiscordCommand cmd)) {
            return false;
        }
        return cmd.isDismissed(player.getUniqueId());
    }

    private void sendClickableNotification(Player player) {
        if (Bukkit.getPluginManager().getPlugin("ZDiscord") == null) return;

        String current = plugin.getDescription().getVersion();
        net.md_5.bungee.api.chat.TextComponent notice = new net.md_5.bungee.api.chat.TextComponent(
                "§b[ZDiscord] §fUpdate available: §7v" + current + " §8→ §av" + latestVersion + "  ");
        net.md_5.bungee.api.chat.TextComponent link = new net.md_5.bungee.api.chat.TextComponent("§e[Download]");
        link.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.OPEN_URL, PAGE_URL));
        net.md_5.bungee.api.chat.TextComponent dismiss = new net.md_5.bungee.api.chat.TextComponent("  §8[Dismiss]");
        dismiss.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/zdiscord update dismiss"));
        notice.addExtra(link);
        notice.addExtra(dismiss);
        player.spigot().sendMessage(notice);
    }

    public void shutdown() {
        running = false;
        timer.cancel();
        HandlerList.unregisterAll(this);
    }

    public static String fetchLatestSync() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            if (connection.getResponseCode() != 200) return null;

            JSONArray versions;
            try (InputStreamReader reader = new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8)) {
                versions = (JSONArray) new JSONParser().parse(reader);
            }
            if (versions.isEmpty()) return null;
            return (String) ((JSONObject) versions.get(0)).get("version_number");
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}

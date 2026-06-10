package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class UpdateChecker implements Listener {

    private static final String PROJECT_SLUG = "zdiscord";
    private static final String API_URL =
            "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String PAGE_URL = "https://modrinth.com/project/" + PROJECT_SLUG;

    private static final long REPEAT_CHECK_TICKS = 20L * 60L * 60L * 5L;
    private static final Pattern VERSION_TOKEN =
            Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?(?:-(.+))?");

    private final ZDiscord plugin;
    private volatile String latestVersion;
    private volatile String latestVersionTitle;
    private volatile String releaseDate;
    private volatile boolean updateAvailable;
    private volatile int lastCheckErrorCount;


    private final AtomicBoolean discordAnnouncedForCurrent = new AtomicBoolean(false);

    public UpdateChecker(ZDiscord plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getPlatformAdapter().runAsyncTimer(this::checkForUpdates,
                200L, REPEAT_CHECK_TICKS);
    }

    private void checkForUpdates() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent",
                    "DemonZ-Development/ZDiscord (" + plugin.getDescription().getVersion() + ")");
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);

            if (connection.getResponseCode() != 200) {
                lastCheckErrorCount++;
                return;
            }

            JSONArray versions = (JSONArray) new JSONParser().parse(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            if (versions.isEmpty()) {
                return;
            }
            JSONObject latest = (JSONObject) versions.get(0);
            latestVersion = (String) latest.get("version_number");
            latestVersionTitle = (String) latest.get("name");
            releaseDate = (String) latest.get("date_published");

            String currentVersion = plugin.getDescription().getVersion();
            if (isNewer(latestVersion, currentVersion)) {
                updateAvailable = true;




                discordAnnouncedForCurrent.set(false);
                plugin.getLogger().info("A new version of ZDiscord is available: v"
                        + latestVersion + " (" + PAGE_URL + ")");
            } else {
                updateAvailable = false;
            }





            if (updateAvailable
                    && !plugin.getConfigManager().getBoolean("misc.update-silent", false)
                    && discordAnnouncedForCurrent.compareAndSet(false, true)) {
                postSilentDiscordNotice(currentVersion);
            }
        } catch (Exception e) {
            lastCheckErrorCount++;
            plugin.debug("Update check failed: " + e.getMessage());
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }


    private void postSilentDiscordNotice(String currentVersion) {
        if (!plugin.getBotManager().isConnected()) {
            discordAnnouncedForCurrent.set(false);
            return;
        }
        String channelId = plugin.getConfigManager()
                .getString("misc.update-channel", "").trim();
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
            return;
        }
        TextChannel channel = plugin.getBotManager().getJda()
                .getTextChannelById(channelId);
        if (channel == null) {
            plugin.debug("Update-check Discord channel '" + channelId
                    + "' not found.");
            return;
        }
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(":arrows_counterclockwise: ZDiscord update available")
                .setDescription("A new version of **ZDiscord** is available. "
                        + "No action is required â€” this is a quiet notice.\n\n"
                        + "Installed: `v" + currentVersion + "`\n"
                        + "Latest: `v" + latestVersion + "`")
                .setColor(0xF1C40F)
                .addField("Changelog", PAGE_URL + "/changelog", false)
                .addField("Download", PAGE_URL + "/versions", false)
                .setTimestamp(Instant.now())
                .setFooter("Silent update check", null);
        channel.sendMessageEmbeds(embed.build()).queue(
                success -> plugin.debug("Posted silent update notice to "
                        + channelId),
                error -> {
                    discordAnnouncedForCurrent.set(false);
                    plugin.debug("Failed to post silent update notice: "
                            + error.getMessage());
                });
    }


    public static boolean isNewer(String candidate, String current) {
        if (candidate == null || current == null) {
            return false;
        }
        if (candidate.equals(current)) {
            return false;
        }
        int[] c = parseVersion(candidate);
        int[] m = parseVersion(current);
        if (c == null || m == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (c[i] != m[i]) {
                return c[i] > m[i];
            }
        }


        String cPre = preRelease(candidate);
        String mPre = preRelease(current);
        if (cPre == null && mPre != null) {
            return true;
        }
        if (cPre != null && mPre == null) {
            return false;
        }
        return false;
    }

    private static int[] parseVersion(String version) {
        Matcher m = VERSION_TOKEN.matcher(version);
        if (!m.find()) {
            return null;
        }
        int major = Integer.parseInt(m.group(1));
        int minor = Integer.parseInt(m.group(2));
        int patch = m.group(3) != null ? Integer.parseInt(m.group(3)) : 0;
        return new int[] { major, minor, patch };
    }

    private static String preRelease(String version) {
        Matcher m = VERSION_TOKEN.matcher(version);
        if (!m.find()) {
            return null;
        }
        return m.group(4);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!updateAvailable) {
            return;
        }




        if (plugin.getConfigManager().getBoolean("misc.update-silent", false)) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("zdiscord.admin")) {
            return;
        }
        if (isDismissedByPlayer(player)) {
            return;
        }
        sendClickableNotification(player);
    }

    private boolean isDismissedByPlayer(Player player) {
        if (plugin.getCommand("zdiscord") == null) {
            return false;
        }
        if (!(plugin.getCommand("zdiscord").getExecutor()
                instanceof dev.demonz.zdiscord.minecraft.commands.ZDiscordCommand cmd)) {
            return false;
        }
        return cmd.isDismissed(player.getUniqueId());
    }

    private void sendClickableNotification(Player player) {
        if (Bukkit.getPluginManager().getPlugin("ZDiscord") == null) {
            return;
        }
        String current = plugin.getDescription().getVersion();

        Component prefix = Component.text("[ZDiscord] ", NamedTextColor.AQUA)
                .append(Component.text("Update available: ", NamedTextColor.WHITE))
                .append(Component.text("v" + current, NamedTextColor.GRAY))
                .append(Component.text(" \u2192 ", NamedTextColor.DARK_GRAY))
                .append(Component.text("v" + latestVersion, NamedTextColor.GREEN)
                        .decoration(TextDecoration.BOLD, true));

        Component link = Component.text("[" + PAGE_URL + "]", NamedTextColor.YELLOW)
                .clickEvent(ClickEvent.openUrl(PAGE_URL))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Click to open the ZDiscord download page in your browser.",
                        NamedTextColor.GRAY)));

        Component separator = Component.text("  ", NamedTextColor.DARK_GRAY);

        Component dismiss = Component.text("[Dismiss]", NamedTextColor.DARK_GRAY)
                .clickEvent(ClickEvent.runCommand("/zdiscord update dismiss"))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Stop showing this banner for the rest of the session.",
                        NamedTextColor.GRAY)));

        Component message = prefix.append(separator).append(link).append(separator).append(dismiss);
        player.sendMessage(message);
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public int getLastCheckErrorCount() {
        return lastCheckErrorCount;
    }


    public CompletableFuture<String> fetchLatestVersion() {
        CompletableFuture<String> future = new CompletableFuture<>();
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                future.complete(fetchLatestSync());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future.orTimeout(10, TimeUnit.SECONDS);
    }


    public static String fetchLatestSync() {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setConnectTimeout(5_000);
            connection.setReadTimeout(5_000);
            if (connection.getResponseCode() != 200) {
                return null;
            }
            JSONArray versions = (JSONArray) new JSONParser().parse(
                    new InputStreamReader(
                            connection.getInputStream(), StandardCharsets.UTF_8));
            if (versions.isEmpty()) {
                return null;
            }
            return (String) ((JSONObject) versions.get(0)).get("version_number");
        } catch (Exception e) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

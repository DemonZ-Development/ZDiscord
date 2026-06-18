package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import dev.demonz.zdiscord.util.ZLogger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class JoinQuitListener implements Listener {

    private final ZDiscord plugin;
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    public JoinQuitListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();




        final boolean firstJoin;
        long existingFirstJoin = plugin.getStorageManager().getFirstJoin(uuid);
        firstJoin = (existingFirstJoin == 0);



        plugin.getPlatformAdapter().runAsync(() -> {
            plugin.getStorageManager().setLastSeen(uuid, now);
            plugin.getStorageManager().incrementSessions(uuid);
            if (firstJoin) {
                plugin.getStorageManager().setFirstJoin(uuid, now);
            }
        });




        joinTimestamps.put(uuid, now);

        if (plugin.getBotManager() != null
                && plugin.getBotManager().isConnected()
                && plugin.getConfigManager().getBoolean("events.join.enabled", true)) {
            sendEmbed(player, true, null, firstJoin);
        }

        if (firstJoin
                && plugin.getConfigManager().getBoolean("events.join.show-first-join-indicator", true)) {
            String welcome = plugin.getConfigManager().getString(
                    "events.join.first-join-message", "");
            if (!welcome.isEmpty()) {
                String resolved = ColorUtil.stripColor(
                        welcome
                                .replace("%player%", player.getName())
                                .replace("%displayname%", ColorUtil.stripColor(player.getDisplayName()))
                                .replace("%uuid%", player.getUniqueId().toString()));
                plugin.getPlatformAdapter().runLater(
                        () -> player.sendMessage(resolved), 20L);
            }
        }

        if (plugin.getAntiRaidModule() != null) {
            plugin.getAntiRaidModule().onPlayerJoin(player);
        }


        if (plugin.getFollowModule() != null) {
            plugin.getFollowModule().onPlayerJoin(player);
        }

        if (plugin.getBotManager() != null) {
            plugin.getPlatformAdapter().runAsync(() -> plugin.getBotManager().updateActivity());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();



        if (plugin.getBotManager() != null) {
            plugin.getPlatformAdapter().runLater(
                    () -> plugin.getPlatformAdapter().runAsync(
                            () -> plugin.getBotManager().updateActivity()),
                    2L);
        }

        Long joinTime = joinTimestamps.remove(uuid);
        if (joinTime != null && plugin.getLeaderboardModule() != null) {
            long sessionSeconds = (now - joinTime) / 1000L;
            if (sessionSeconds > 0) {
                plugin.getLeaderboardModule().incrementStatBy(
                        uuid, "playtime", sessionSeconds);
            }
        }


        plugin.getPlatformAdapter().runAsync(
                () -> plugin.getStorageManager().setLastSeen(uuid, now));

        if (plugin.getBotManager() != null
                && plugin.getBotManager().isConnected()
                && plugin.getConfigManager().getBoolean("events.quit.enabled", true)) {
            sendEmbed(player, false, joinTime, false);
        }
    }

    private void sendEmbed(Player player, boolean joined, Long knownJoinTime, boolean firstJoin) {
        TextChannel channel = resolveEventChannel();
        if (channel == null) {
            return;
        }

        String typeKey = joined ? "join" : "quit";
        String colorHex = plugin.getConfigManager().getString(
                "events." + typeKey + ".color",
                joined ? "#2ECC71" : "#E74C3C");

        String title = joined ? "Player Joined" : "Player Left";
        String verb = joined ? "joined" : "left";


        String avatarUrl = HeadUtil.avatar(player.getUniqueId(), HeadUtil.SIZE_MEDIUM);


        String footerIcon = plugin.getConfigManager().getString(
                "events." + typeKey + ".footer-icon", "");
        if (footerIcon.isEmpty()) {
            footerIcon = plugin.getConfigManager().getString("events.footer-icon", "");
        }
        if (footerIcon.isEmpty()) {
            String guildIcon = channel.getGuild().getIconUrl();
            if (guildIcon != null) {
                footerIcon = guildIcon + "?size=64";
            }
        }


        int currentOnline = plugin.getServer().getOnlinePlayers().size();
        int maxOnline = plugin.getServer().getMaxPlayers();
        int prevOnline = currentOnline - (joined ? 1 : -1);

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName(),
                        "https://namemc.com/profile/" + player.getUniqueId(),
                        avatarUrl)
                .setTitle((joined ? ":green_circle: " : ":red_circle: ") + title)
                .setDescription("**" + player.getName() + "** " + verb + " the server")
                .setColor(ColorUtil.parseHex(colorHex))
                .setThumbnail(avatarUrl)
                .addField("Player", "`" + player.getName() + "`", true)
                .addField("Online",
                        currentOnline + "/" + maxOnline + " (was " + prevOnline + ")", true)
                .addField("Status",
                        joined ? ":white_check_mark: Online" : ":x: Offline", true)
                .setFooter("**" + player.getName() + "** " + verb + " the server",
                        footerIcon.isEmpty() ? null : footerIcon)
                .setTimestamp(Instant.now());

        if (joined && firstJoin
                && plugin.getConfigManager().getBoolean("events.join.show-first-join-indicator", true)) {
            embed.addField(":sparkles: New Player",
                    "Welcome! This is **" + player.getName() + "**'s first join.", false);
        }


        if (!joined) {
            long seconds = -1;
            if (knownJoinTime != null) {
                seconds = (System.currentTimeMillis() - knownJoinTime) / 1000L;
            }
            if (seconds > 0) {
                String duration = formatDuration(seconds);
                embed.addField("Session", ":hourglass: " + duration, false);
            }
        }

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> { },
                error -> ZLogger.debug(ZLogger.Category.EVENTS,
                        "Failed to send " + typeKey + " embed: " + error.getMessage()));
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m " + (seconds % 60) + "s";
        }
        long hours = minutes / 60;
        minutes = minutes % 60;
        if (hours < 24) {
            return hours + "h " + minutes + "m";
        }
        long days = hours / 24;
        hours = hours % 24;
        return days + "d " + hours + "h";
    }

    private TextChannel resolveEventChannel() {
        if (plugin.getBotManager() == null) {
            return null;
        }
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        return channel;
    }
}

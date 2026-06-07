/*
 * Copyright 2026 DemonZ Development
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

package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
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

/**
 * Forwards join and quit events to Discord as polished embeds and
 * tracks per-player session duration for the playtime leaderboard.
 */
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

        // Use storage to detect first join — hasPlayedBefore()
        // is unreliable after /reload because Bukkit clears its
        // offline-player cache.  If storage has no firstJoin
        // record yet, this is genuinely the first time we've
        // seen the player.
        final boolean firstJoin;
        long existingFirstJoin = plugin.getStorageManager().getFirstJoin(uuid);
        if (existingFirstJoin == 0) {
            firstJoin = true;
        } else {
            firstJoin = false;
        }

        // Persist activity timestamps + increment session counter
        // off the main thread so a slow disk can't stall the join.
        plugin.getPlatformAdapter().runAsync(() -> {
            plugin.getStorageManager().setLastSeen(uuid, now);
            plugin.getStorageManager().incrementSessions(uuid);
            if (firstJoin) {
                plugin.getStorageManager().setFirstJoin(uuid, now);
            }
        });

        if (plugin.getBotManager().isConnected()
                && plugin.getConfigManager().getBoolean("events.join.enabled", true)) {
            sendEmbed(player, true, null, firstJoin);
        }

        joinTimestamps.put(uuid, now);

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

        // The first-join welcome is also mirrored to Discord as
        // a private DM-style embed; we only render the indicator
        // field there so the player can see the celebration in
        // the events channel. Color codes in the template are
        // converted to Discord markdown so the operator's choice
        // of &l/&n still renders properly.

        if (plugin.getAntiRaidModule() != null) {
            plugin.getAntiRaidModule().onPlayerJoin(player);
        }

        // Notify any Discord users following this player. Done
        // after the activity write so the follow storage is
        // guaranteed to be initialized.
        if (plugin.getFollowModule() != null) {
            plugin.getFollowModule().onPlayerJoin(player);
        }

        plugin.getPlatformAdapter().runAsync(() -> plugin.getBotManager().updateActivity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Defer the activity update and online count by one tick so the
        // player is actually removed from the online list when we read it.
        plugin.getPlatformAdapter().runLater(
                () -> plugin.getPlatformAdapter().runAsync(
                        () -> plugin.getBotManager().updateActivity()),
                2L);

        Long joinTime = joinTimestamps.remove(uuid);
        if (joinTime != null && plugin.getLeaderboardModule() != null) {
            long sessionSeconds = (now - joinTime) / 1000L;
            if (sessionSeconds > 0) {
                plugin.getLeaderboardModule().incrementStatBy(
                        uuid, "playtime", sessionSeconds);
            }
        }

        // Update last-seen on quit too — covers the rare case where
        // a player disconnects abnormally (crash) and never fires
        // a follow-up join, leaving the join's timestamp stale.
        plugin.getPlatformAdapter().runAsync(
                () -> plugin.getStorageManager().setLastSeen(uuid, now));

        if (plugin.getBotManager().isConnected()
                && plugin.getConfigManager().getBoolean("events.quit.enabled", true)) {
            // Compute session duration before sending so sendEmbed can include it.
            sendEmbed(player, false, joinTime, false);
        }
    }

    private void sendEmbed(Player player, boolean joined, Long knownJoinTime, boolean firstJoin) {
        TextChannel channel = resolveEventChannel();
        if (channel == null) {
            return;
        }

        String typeKey = joined ? "join" : "quit";
        String template = plugin.getConfigManager().getString(
                "events." + typeKey + ".message",
                joined ? "**%player%** joined the server"
                       : "**%player%** left the server");
        String colorHex = plugin.getConfigManager().getString(
                "events." + typeKey + ".color",
                joined ? "#2ECC71" : "#E74C3C");

        String title = joined
                ? "Player Joined"
                : "Player Left";
        String verb = joined ? "joined" : "left";

        String avatar = resolveAvatar(player);
        String resolvedMessage = ColorUtil.toDiscordMarkdown(
                template
                        .replace("%player%", player.getName())
                        .replace("%displayname%", ColorUtil.stripColor(player.getDisplayName())));

        // Footer icon: configurable, with three layers of fallback.
        //   1. events.<type>.footer-icon in config.yml
        //   2. events.footer-icon (shared)
        //   3. the guild's icon
        //   4. null (Discord will render the footer with no icon)
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

        // Online count delta so the channel can see traffic at a glance.
        int currentOnline = plugin.getServer().getOnlinePlayers().size();
        int maxOnline = plugin.getServer().getMaxPlayers();
        int delta = joined ? +1 : -1;
        int prevOnline = currentOnline - delta;
        String deltaSuffix = joined
                ? " (was " + prevOnline + ")"
                : " (was " + prevOnline + ")";

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName(),
                        "https://namemc.com/profile/" + player.getUniqueId(),
                        avatar)
                .setTitle((joined ? ":green_circle: " : ":red_circle: ") + title)
                .setDescription("**" + player.getName() + "** " + verb + " the server")
                .setColor(ColorUtil.parseHex(colorHex))
                .setThumbnail(avatar)
                .addField("Player", "`" + player.getName() + "`", true)
                .addField("Online",
                        currentOnline + "/" + maxOnline + deltaSuffix, true)
                .addField("Status",
                        joined ? ":white_check_mark: Online" : ":x: Offline", true)
                .setFooter(resolvedMessage, footerIcon.isEmpty() ? null : footerIcon)
                .setTimestamp(Instant.now());

        if (joined && firstJoin
                && plugin.getConfigManager().getBoolean("events.join.show-first-join-indicator", true)) {
            embed.addField(":sparkles: New Player",
                    "Welcome! This is **" + player.getName() + "**'s first join.", false);
        }

        // Add session duration on quit.
        if (!joined) {
            long seconds = -1;
            if (knownJoinTime != null) {
                seconds = (System.currentTimeMillis() - knownJoinTime) / 1000L;
            } else {
                Long joinTime = joinTimestamps.get(player.getUniqueId());
                if (joinTime != null) {
                    seconds = (System.currentTimeMillis() - joinTime) / 1000L;
                }
            }
            if (seconds > 0) {
                String duration = formatDuration(seconds);
                embed.addField("Session", ":hourglass: " + duration, false);
            }
        }

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> { },
                error -> plugin.debug("Failed to send "
                        + typeKey + " embed: " + error.getMessage()));
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
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        return channel;
    }

    private String resolveAvatar(Player player) {
        String format = plugin.getConfigManager()
                .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
        return HeadUtil.resolve(format, player.getUniqueId(), player.getName());
    }
}

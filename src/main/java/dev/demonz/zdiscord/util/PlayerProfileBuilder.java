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

package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.modules.LinkModule;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Instant;
import java.util.UUID;

/**
 * Renders a rich "player passport" embed used by the
 * {@code /profile} slash command.
 *
 * <p>Pulls data from storage (first/last seen, sessions,
 * achievements) and the live Bukkit API (online status). All
 * operations are read-only and run on the caller thread, so
 * the caller is responsible for offloading to an async task
 * when desired.</p>
 */
public final class PlayerProfileBuilder {

    private PlayerProfileBuilder() {
    }

    public static Profile build(ZDiscord plugin, OfflinePlayer target) {
        Profile p = new Profile();
        if (target == null) {
            return p;
        }
        p.uuid = target.getUniqueId();
        p.name = target.getName() != null ? target.getName() : "Unknown";
        p.online = target.isOnline();
        p.firstJoinMs = plugin.getStorageManager().getFirstJoin(p.uuid);
        p.lastSeenMs = plugin.getStorageManager().getLastSeen(p.uuid);
        p.sessions = plugin.getStorageManager().getSessions(p.uuid);
        p.advancementCount = plugin.getStorageManager().getPlayerAdvancementCount(p.uuid);

        LinkModule link = plugin.getLinkModule();
        if (link != null) {
            p.discordId = link.getDiscordId(p.uuid);
        }
        p.followerCount = plugin.getFollowModule() != null
                ? plugin.getFollowModule().getFollowerCount(p.uuid)
                : 0;

        // Total playtime (seconds) is a leaderboard stat; we just read it.
        if (plugin.getLeaderboardModule() != null) {
            long ptSec = plugin.getLeaderboardModule()
                    .getStat(p.uuid, "playtime");
            p.playtimeSeconds = ptSec;
        }
        return p;
    }

    public static EmbedBuilder toEmbed(ZDiscord plugin, Profile profile, String requestedBy) {
        String avatar = HeadUtil.crafatar(profile.uuid);

        String color = plugin.getConfigManager().getString(
                "profile.embed.color", "#9B59B6");
        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(profile.name + "  ·  Player profile",
                        "https://namemc.com/profile/" + profile.uuid,
                        avatar)
                .setThumbnail(avatar)
                .setColor(ColorUtil.parseHex(color))
                .setFooter("Requested by " + requestedBy, null)
                .setTimestamp(Instant.now());

        embed.addField(":bust_in_silhouette: Identity",
                "Name: `" + profile.name + "`\n"
                + "UUID: `" + profile.uuid + "`\n"
                + "Status: " + (profile.online
                        ? ":green_circle: Online"
                        : ":red_circle: Offline"),
                false);

        String linkedLine = profile.discordId != null
                ? ":link: Linked to Discord"
                : ":no_entry_sign: Not linked";
        String playtimeLine = formatDuration(profile.playtimeSeconds);
        embed.addField(":bar_chart: Activity",
                "First seen: " + formatDate(profile.firstJoinMs) + "\n"
                + "Last seen: " + formatDate(profile.lastSeenMs) + "\n"
                + "Sessions: " + profile.sessions + "\n"
                + "Total playtime: " + playtimeLine + "\n"
                + linkedLine,
                true);

        embed.addField(":trophy: Achievements & Social",
                "Advancements: " + profile.advancementCount + "\n"
                + "Followers: " + profile.followerCount,
                true);

        if (profile.discordId != null) {
            // Try to resolve the Discord username. The caller
            // should have set this; if not, fall back to the raw ID.
            String label = profile.discordUsername != null
                    ? profile.discordUsername + "  (`" + profile.discordId + "`)"
                    : "`" + profile.discordId + "`";
            embed.addField(":globe_with_meridians: Discord",
                    label, false);
        }
        return embed;
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) {
            return "0m";
        }
        long s = seconds;
        long days = s / 86400;
        s %= 86400;
        long hours = s / 3600;
        s %= 3600;
        long minutes = s / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static String formatDate(long millis) {
        if (millis <= 0) {
            return "unknown";
        }
        return "<t:" + (millis / 1000L) + ":R>";
    }

    public static OfflinePlayer findOfflineByName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        // Fast path: a currently online player
        var online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        // Slow path: look in the offline player cache
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            if (op.getName() != null && op.getName().equalsIgnoreCase(name)) {
                return op;
            }
        }
        return null;
    }

    public static class Profile {
        public UUID uuid;
        public String name;
        public boolean online;
        public long firstJoinMs;
        public long lastSeenMs;
        public long sessions;
        public int advancementCount;
        public int followerCount;
        public long playtimeSeconds;
        public String discordId;
        public String discordUsername;
    }
}

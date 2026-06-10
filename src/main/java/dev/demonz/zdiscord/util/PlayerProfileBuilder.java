package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.modules.LinkModule;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.time.Instant;
import java.util.UUID;

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


        if (plugin.getLeaderboardModule() != null) {
            p.playtimeSeconds = plugin.getLeaderboardModule().getStat(p.uuid, "playtime");
            p.kills = plugin.getLeaderboardModule().getStat(p.uuid, "kills");
            p.deaths = plugin.getLeaderboardModule().getStat(p.uuid, "deaths");
        }
        return p;
    }


    public static EmbedBuilder toEmbed(ZDiscord plugin, Profile profile, String requestedBy) {
        String avatarSmall = HeadUtil.avatar(profile.uuid, HeadUtil.SIZE_SMALL);
        String avatarLarge = HeadUtil.avatar(profile.uuid, HeadUtil.SIZE_LARGE);

        String color = plugin.getConfigManager().getString(
                "profile.embed.color", "#9B59B6");

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(profile.name + "  Â·  Player profile",
                        "https://namemc.com/profile/" + profile.uuid,
                        avatarSmall)
                .setThumbnail(avatarLarge)
                .setColor(ColorUtil.parseHex(color))
                .setFooter("Requested by " + requestedBy
                        + "  â€¢  ZDiscord v" + plugin.getDescription().getVersion(), null)
                .setTimestamp(Instant.now());


        String statusEmoji = profile.online ? ":green_circle:" : ":red_circle:";
        String statusText = profile.online ? "Online" : "Offline";
        embed.addField(":bust_in_silhouette: Identity",
                "**Name:** `" + profile.name + "`\n"
                + "**UUID:** `" + profile.uuid + "`\n"
                + "**Status:** " + statusEmoji + " " + statusText,
                false);


        String linkedLine = profile.discordId != null
                ? ":link: Linked to Discord"
                : ":no_entry_sign: Not linked";
        String playtimeLine = formatDuration(profile.playtimeSeconds);
        embed.addField(":bar_chart: Activity",
                "**First seen:** " + formatDate(profile.firstJoinMs) + "\n"
                + "**Last seen:** " + formatDate(profile.lastSeenMs) + "\n"
                + "**Sessions:** " + profile.sessions + "\n"
                + "**Playtime:** " + playtimeLine + "\n"
                + linkedLine,
                true);


        double kd = profile.deaths > 0
                ? Math.round((double) profile.kills / profile.deaths * 100.0) / 100.0
                : profile.kills;
        embed.addField(":crossed_swords: Combat",
                "**Kills:** " + profile.kills + "\n"
                + "**Deaths:** " + profile.deaths + "\n"
                + "**K/D Ratio:** " + String.format("%.2f", kd),
                true);


        embed.addField(":trophy: Achievements & Social",
                "**Advancements:** " + profile.advancementCount + "\n"
                + "**Followers:** " + profile.followerCount,
                true);


        if (profile.discordId != null) {
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

        var online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }

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
        public long kills;
        public long deaths;
        public String discordId;
        public String discordUsername;
    }
}

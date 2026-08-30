package dev.demonz.zdiscord.api;

import dev.demonz.zdiscord.api.model.LeaderboardEntry;
import dev.demonz.zdiscord.api.model.PlayerProfile;
import dev.demonz.zdiscord.api.model.EmbedData;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Public API for other plugins to hook into ZDiscord.
 * Obtain an instance via {@link ZDiscordProvider#get()}.
 */
public interface ZDiscordAPI {

    PlayerProfile getPlayerProfile(UUID uuid);

    List<LeaderboardEntry> getLeaderboard(String stat, int limit);

    Set<String> getFollowers(UUID playerUUID);

    Set<UUID> getFollowedPlayers(String discordId);

    boolean isLinked(UUID playerUUID);

    String getLinkedDiscordId(UUID playerUUID);

    UUID getLinkedMinecraftUUID(String discordId);

    long getStatValue(UUID playerUUID, String stat);

    String getPluginVersion();

    boolean isBotConnected();

    /** Queue a plain message to the channel ID stored at the given config path. */
    boolean sendMessage(String channelConfigPath, String message);

    /** Queue an embed to the channel ID stored at the given config path. */
    boolean sendEmbed(String channelConfigPath, EmbedData embed);

    int getOnlinePlayerCount();

    void incrementStat(UUID playerUUID, String stat, long amount);

    void setStat(UUID playerUUID, String stat, long value);
}

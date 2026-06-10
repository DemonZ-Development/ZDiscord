package dev.demonz.zdiscord.api;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.model.LeaderboardEntry;
import dev.demonz.zdiscord.api.model.PlayerProfile;
import dev.demonz.zdiscord.modules.FollowModule;
import dev.demonz.zdiscord.modules.LeaderboardModule;
import dev.demonz.zdiscord.modules.LinkModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ZDiscordAPIImpl implements ZDiscordAPI {

    private final ZDiscord plugin;

    public ZDiscordAPIImpl(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public PlayerProfile getPlayerProfile(UUID uuid) {
        if (uuid == null) return null;
        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        var internal = dev.demonz.zdiscord.util.PlayerProfileBuilder.build(plugin, op);
        return new PlayerProfile(
                internal.uuid, internal.name, internal.online,
                internal.firstJoinMs, internal.lastSeenMs, internal.sessions,
                internal.advancementCount, internal.followerCount,
                internal.playtimeSeconds, internal.kills, internal.deaths,
                internal.discordId);
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(String stat, int limit) {
        LeaderboardModule lm = plugin.getLeaderboardModule();
        if (lm == null) return Collections.emptyList();
        List<Map.Entry<UUID, Long>> raw = lm.getLeaderboard(stat, limit);
        List<LeaderboardEntry> result = new ArrayList<>(raw.size());
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : raw) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            result.add(new LeaderboardEntry(
                    entry.getKey(),
                    name != null ? name : "Unknown",
                    stat, entry.getValue(), rank++));
        }
        return result;
    }

    @Override
    public Set<String> getFollowers(UUID playerUUID) {
        FollowModule fm = plugin.getFollowModule();
        if (fm == null) return Collections.emptySet();
        return plugin.getStorageManager().getFollowers(playerUUID);
    }

    @Override
    public Set<UUID> getFollowedPlayers(String discordId) {
        FollowModule fm = plugin.getFollowModule();
        if (fm == null) return Collections.emptySet();
        return fm.getFollowedPlayers(discordId);
    }

    @Override
    public boolean isLinked(UUID playerUUID) {
        LinkModule lm = plugin.getLinkModule();
        return lm != null && lm.getDiscordId(playerUUID) != null;
    }

    @Override
    public String getLinkedDiscordId(UUID playerUUID) {
        LinkModule lm = plugin.getLinkModule();
        return lm != null ? lm.getDiscordId(playerUUID) : null;
    }

    @Override
    public UUID getLinkedMinecraftUUID(String discordId) {
        LinkModule lm = plugin.getLinkModule();
        return lm != null ? lm.getPlayerUUID(discordId) : null;
    }

    @Override
    public long getStatValue(UUID playerUUID, String stat) {
        LeaderboardModule lm = plugin.getLeaderboardModule();
        return lm != null ? lm.getStat(playerUUID, stat) : 0L;
    }

    @Override
    public String getPluginVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean isBotConnected() {
        return plugin.getBotManager() != null && plugin.getBotManager().isConnected();
    }

    @Override
    public int getOnlinePlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public void incrementStat(UUID playerUUID, String stat, long amount) {
        LeaderboardModule lm = plugin.getLeaderboardModule();
        if (lm != null) {
            lm.incrementStatBy(playerUUID, stat, amount);
        }
    }

    @Override
    public void setStat(UUID playerUUID, String stat, long value) {
        LeaderboardModule lm = plugin.getLeaderboardModule();
        if (lm != null) {
            lm.setStat(playerUUID, stat, value);
        }
    }
}

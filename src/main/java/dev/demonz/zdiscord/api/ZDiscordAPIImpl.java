package dev.demonz.zdiscord.api;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.model.LeaderboardEntry;
import dev.demonz.zdiscord.api.model.PlayerProfile;
import dev.demonz.zdiscord.api.model.EmbedData;
import dev.demonz.zdiscord.modules.FollowModule;
import dev.demonz.zdiscord.modules.LeaderboardModule;
import dev.demonz.zdiscord.modules.LinkModule;
import dev.demonz.zdiscord.util.PlayerProfileBuilder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.ZLogger;

public final class ZDiscordAPIImpl implements ZDiscordAPI {

    private final ZDiscord plugin;

    public ZDiscordAPIImpl(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public PlayerProfile getPlayerProfile(UUID uuid) {
        if (uuid == null) return null;
        var internal = PlayerProfileBuilder.build(plugin, Bukkit.getOfflinePlayer(uuid));
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

        List<LeaderboardEntry> result = new ArrayList<>();
        int rank = 1;
        for (Map.Entry<UUID, Long> entry : lm.getLeaderboard(stat, limit)) {
            String name = Bukkit.getOfflinePlayer(entry.getKey()).getName();
            result.add(new LeaderboardEntry(
                    entry.getKey(), name != null ? name : "Unknown",
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
    public boolean sendMessage(String channelConfigPath, String message) {
        TextChannel channel = resolveChannel(channelConfigPath);
        if (channel == null || message == null || message.isBlank()) return false;
        String content = message.length() <= 2000 ? message : message.substring(0, 1999) + "\u2026";
        channel.sendMessage(content)
                .setAllowedMentions(Collections.<Message.MentionType>emptyList())
                .queue(null, error -> ZLogger.warn(ZLogger.Category.API,
                        "API message failed: " + error.getMessage()));
        return true;
    }

    @Override
    public boolean sendEmbed(String channelConfigPath, EmbedData data) {
        TextChannel channel = resolveChannel(channelConfigPath);
        if (channel == null || data == null) return false;
        try {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(data.getTitle())
                    .setDescription(data.getDescription())
                    .setColor(ColorUtil.parseHex(data.getColor()))
                    .setThumbnail(data.getThumbnailUrl())
                    .setFooter(data.getFooterText())
                    .setTimestamp(Instant.now());
            if (data.getAuthorName() != null) {
                embed.setAuthor(data.getAuthorName(), data.getAuthorUrl(), data.getAuthorIconUrl());
            }
            for (EmbedData.Field field : data.getFields()) {
                embed.addField(field.getName(), field.getValue(), field.isInline());
            }
            channel.sendMessageEmbeds(embed.build()).queue(null,
                    error -> ZLogger.warn(ZLogger.Category.API,
                            "API embed failed: " + error.getMessage()));
            return true;
        } catch (RuntimeException e) {
            ZLogger.warn(ZLogger.Category.API, "Rejected invalid API embed: " + e.getMessage());
            return false;
        }
    }

    private TextChannel resolveChannel(String configPath) {
        if (configPath == null || configPath.isBlank()
                || plugin.getBotManager() == null || !plugin.getBotManager().isConnected()) {
            return null;
        }
        return plugin.getBotManager().getTextChannel(configPath);
    }

    @Override
    public int getOnlinePlayerCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public void incrementStat(UUID playerUUID, String stat, long amount) {
        LeaderboardModule lm = plugin.getLeaderboardModule();
        if (lm != null) lm.incrementStatBy(playerUUID, stat, amount);
    }

    @Override
    public void setStat(UUID playerUUID, String stat, long value) {
        LeaderboardModule lm = plugin.getLeaderboardModule();
        if (lm != null) lm.setStat(playerUUID, stat, value);
    }
}

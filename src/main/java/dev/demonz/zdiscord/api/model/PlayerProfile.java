package dev.demonz.zdiscord.api.model;

import java.util.UUID;

public final class PlayerProfile {

    private final UUID uuid;
    private final String name;
    private final boolean online;
    private final long firstJoinMs;
    private final long lastSeenMs;
    private final long sessions;
    private final int advancementCount;
    private final int followerCount;
    private final long playtimeSeconds;
    private final long kills;
    private final long deaths;
    private final String linkedDiscordId;

    public PlayerProfile(UUID uuid, String name, boolean online,
                         long firstJoinMs, long lastSeenMs, long sessions,
                         int advancementCount, int followerCount,
                         long playtimeSeconds, long kills, long deaths,
                         String linkedDiscordId) {
        this.uuid = uuid;
        this.name = name;
        this.online = online;
        this.firstJoinMs = firstJoinMs;
        this.lastSeenMs = lastSeenMs;
        this.sessions = sessions;
        this.advancementCount = advancementCount;
        this.followerCount = followerCount;
        this.playtimeSeconds = playtimeSeconds;
        this.kills = kills;
        this.deaths = deaths;
        this.linkedDiscordId = linkedDiscordId;
    }

    public UUID getUuid() { return uuid; }
    public String getName() { return name; }
    public boolean isOnline() { return online; }
    public long getFirstJoinMs() { return firstJoinMs; }
    public long getLastSeenMs() { return lastSeenMs; }
    public long getSessions() { return sessions; }
    public int getAdvancementCount() { return advancementCount; }
    public int getFollowerCount() { return followerCount; }
    public long getPlaytimeSeconds() { return playtimeSeconds; }
    public long getKills() { return kills; }
    public long getDeaths() { return deaths; }
    public String getLinkedDiscordId() { return linkedDiscordId; }

    public double getKdRatio() {
        return deaths > 0 ? (double) kills / deaths : kills;
    }
}

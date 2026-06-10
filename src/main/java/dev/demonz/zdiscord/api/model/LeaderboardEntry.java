package dev.demonz.zdiscord.api.model;

import java.util.UUID;

public final class LeaderboardEntry {

    private final UUID uuid;
    private final String playerName;
    private final String stat;
    private final long value;
    private final int rank;

    public LeaderboardEntry(UUID uuid, String playerName, String stat,
                            long value, int rank) {
        this.uuid = uuid;
        this.playerName = playerName;
        this.stat = stat;
        this.value = value;
        this.rank = rank;
    }

    public UUID getUuid() { return uuid; }
    public String getPlayerName() { return playerName; }
    public String getStat() { return stat; }
    public long getValue() { return value; }
    public int getRank() { return rank; }
}

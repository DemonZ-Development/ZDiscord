package dev.demonz.zdiscord.storage;

import java.util.List;
import java.util.Map;
import java.util.UUID;


public interface StorageManager {


    void init();


    void shutdown();


    String getTypeName();




    Map<UUID, String> loadLinks();


    void saveLink(UUID playerUUID, String discordId);


    void removeLink(UUID playerUUID);




    Map<UUID, Map<String, Long>> loadStats();


    void saveStat(UUID playerUUID, String stat, long value);


    List<Map.Entry<UUID, Long>> getTopStats(String stat, int limit);




    String getData(String key);


    String getData(String key, String defaultValue);


    int getDataInt(String key, int defaultValue);


    void setData(String key, String value);


    void setData(String key, int value);




    void setLastSeen(UUID playerUUID, long millis);


    long getLastSeen(UUID playerUUID);


    void setFirstJoin(UUID playerUUID, long millis);


    long getFirstJoin(UUID playerUUID);


    void incrementSessions(UUID playerUUID);


    long getSessions(UUID playerUUID);




    void recordAdvancementUnlock(UUID playerUUID, String advancementKey);


    boolean recordAdvancementUnlockIfNew(UUID playerUUID, String advancementKey);


    int getPlayerAdvancementCount(UUID playerUUID);


    int getAdvancementUnlockerCount(String advancementKey);


    int getAdvancementActivePlayerCount();




    void addFollower(UUID playerUUID, String discordId);


    void removeFollower(UUID playerUUID, String discordId);


    java.util.Set<String> getFollowers(UUID playerUUID);


    java.util.Set<UUID> getFollowedPlayers(String discordId);


    boolean isFollowing(UUID playerUUID, String discordId);
}

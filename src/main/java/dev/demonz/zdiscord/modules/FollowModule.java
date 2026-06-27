package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.events.ZDiscordFollowEvent;
import dev.demonz.zdiscord.storage.StorageManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;


public class FollowModule {

    public static final String FOLLOW_BUTTON_ID = "zdiscord:follow";
    public static final String UNFOLLOW_BUTTON_ID = "zdiscord:unfollow";

    private final ZDiscord plugin;


    private final Map<UUID, Set<String>> followers = new HashMap<>();
    private final Map<UUID, Long> lastJoinNotification = new ConcurrentHashMap<>();

    public FollowModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {

    }

    public void reload() {
        synchronized (followers) {
            followers.clear();
        }
    }

    public void shutdown() {


    }



    public boolean isFollowing(UUID playerUUID, String discordId) {
        synchronized (followers) {
            return followers.getOrDefault(playerUUID, Set.of()).contains(discordId);
        }
    }

    public int getFollowerCount(UUID playerUUID) {
        synchronized (followers) {
            return followers.getOrDefault(playerUUID, Set.of()).size();
        }
    }

    public Set<UUID> getFollowedPlayers(String discordId) {





        return plugin.getStorageManager().getFollowedPlayers(discordId);
    }



    public void follow(UUID playerUUID, String discordId) {
        synchronized (followers) {
            followers.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(discordId);
        }
        plugin.getStorageManager().addFollower(playerUUID, discordId);
        Bukkit.getPluginManager().callEvent(
                new ZDiscordFollowEvent(playerUUID, discordId, true));
    }

    public void unfollow(UUID playerUUID, String discordId) {
        synchronized (followers) {
            Set<String> set = followers.get(playerUUID);
            if (set != null) {
                set.remove(discordId);
                if (set.isEmpty()) {
                    followers.remove(playerUUID);
                }
            }
        }
        plugin.getStorageManager().removeFollower(playerUUID, discordId);
        Bukkit.getPluginManager().callEvent(
                new ZDiscordFollowEvent(playerUUID, discordId, false));
    }




    public void onPlayerJoin(Player player) {
        if (!plugin.getConfigManager().getBoolean("follow.enabled", true)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> followerIds = loadFollowersFromStorage(uuid);
        if (followerIds.isEmpty()) {
            return;
        }

        synchronized (followers) {
            followers.put(uuid, new HashSet<>(followerIds));
        }

        String name = player.getName();
        long cooldownMs = plugin.getConfigManager().getInt(
                "follow.join-notification-cooldown", 300) * 1000L;
        Long lastNotify = lastJoinNotification.get(uuid);
        long now = System.currentTimeMillis();
        if (lastNotify != null && (now - lastNotify) < cooldownMs) {
            return;
        }
        lastJoinNotification.put(uuid, now);
        for (String discordId : followerIds) {
            try {
                plugin.getBotManager().getJda().retrieveUserById(discordId).queue(
                        user -> {
                            if (user == null) return;
                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle(":wave: " + name + " just logged in")
                                    .setDescription("**" + name
                                            + "** has just joined the Minecraft server.")
                                    .setColor(0x2ECC71)
                                    .addField("Joined at",
                                            "<t:" + (now / 1000L) + ":F>", false)
                                    .setTimestamp(java.time.Instant.ofEpochMilli(now))
                                    .setFooter("You are following this player", null);
                            user.openPrivateChannel().queue(
                                    ch -> ch.sendMessageEmbeds(embed.build()).queue(
                                            null,
                                            err -> plugin.debug("Failed to DM follower "
                                                    + discordId + ": " + err.getMessage())),
                                    err -> plugin.debug("Failed to open DM channel for "
                                            + discordId + ": " + err.getMessage()));
                        },
                        err -> plugin.debug("Failed to retrieve user " + discordId
                                + ": " + err.getMessage()));
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING,
                        "Follow dispatch failed for " + discordId, e);
            }
        }
    }

    private Set<String> loadFollowersFromStorage(UUID uuid) {
        StorageManager sm = plugin.getStorageManager();
        if (sm == null) {
            return Set.of();
        }
        return sm.getFollowers(uuid);
    }



    public void handleFollowButton(ButtonInteractionEvent event) {

        String id = event.getComponentId();
        String prefix;
        boolean doFollow;
        if (id.startsWith(FOLLOW_BUTTON_ID + ":")) {
            prefix = FOLLOW_BUTTON_ID + ":";
            doFollow = true;
        } else if (id.startsWith(UNFOLLOW_BUTTON_ID + ":")) {
            prefix = UNFOLLOW_BUTTON_ID + ":";
            doFollow = false;
        } else {
            return;
        }
        String raw = id.substring(prefix.length());
        UUID target;
        try {
            target = UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            event.reply("That player is no longer tracked.").setEphemeral(true).queue();
            return;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String name = offline.getName() != null ? offline.getName() : "a player";
        String discordId = event.getUser().getId();

        if (doFollow) {
            follow(target, discordId);
            event.reply(":bell: You will now be notified when **" + name
                    + "** joins the server.").setEphemeral(true).queue();
        } else {
            unfollow(target, discordId);
            event.reply(":no_bell: You will no longer be notified when **" + name
                    + "** joins the server.").setEphemeral(true).queue();
        }
    }

    public Button buildFollowButton(UUID target) {
        return Button.success(FOLLOW_BUTTON_ID + ":" + target, ":bell: Follow");
    }

    public Button buildUnfollowButton(UUID target) {
        return Button.secondary(UNFOLLOW_BUTTON_ID + ":" + target, ":no_bell: Unfollow");
    }
}

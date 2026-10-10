package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.events.ZDiscordFollowEvent;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.components.buttons.Button;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class FollowModule {

    public static final String FOLLOW_BUTTON_ID = "zdiscord:follow";
    public static final String UNFOLLOW_BUTTON_ID = "zdiscord:unfollow";

    private final ZDiscord plugin;
    private final FollowerStore followers;
    private final Map<UUID, Long> lastJoinNotification = new ConcurrentHashMap<>();

    public FollowModule(ZDiscord plugin) {
        this.plugin = plugin;
        this.followers = new FollowerStore(plugin.getStorageManager());
    }

    public boolean isFollowing(UUID playerUUID, String discordId) {
        return followers.contains(playerUUID, discordId);
    }

    public int getFollowerCount(UUID playerUUID) {
        return followers.followers(playerUUID).size();
    }

    public Set<String> getFollowers(UUID playerUUID) {
        return followers.followers(playerUUID);
    }

    public Set<UUID> getFollowedPlayers(String discordId) {
        return followers.followedPlayers(discordId);
    }

    public void follow(UUID playerUUID, String discordId) {
        if (!followers.follow(playerUUID, discordId)) return;
        Bukkit.getPluginManager().callEvent(
                new ZDiscordFollowEvent(playerUUID, discordId, true));
    }

    public void unfollow(UUID playerUUID, String discordId) {
        if (!followers.unfollow(playerUUID, discordId)) return;
        Bukkit.getPluginManager().callEvent(
                new ZDiscordFollowEvent(playerUUID, discordId, false));
    }

    public void onPlayerJoin(Player player) {
        if (!plugin.getConfigManager().getBoolean("follow.enabled", true)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        plugin.getPlatformAdapter().runAsync(() -> notifyFollowers(uuid, name));
    }

    private void notifyFollowers(UUID uuid, String name) {
        Set<String> followerIds = followers.followers(uuid);
        if (followerIds.isEmpty()) {
            return;
        }

        if (plugin.getBotManager() == null || !plugin.getBotManager().isConnected()) return;

        long cooldownMs = plugin.getConfigManager().getInt(
                "follow.join-notification-cooldown", 300) * 1000L;
        long now = System.currentTimeMillis();

        if (lastJoinNotification.size() > 1000) {
            lastJoinNotification.values().removeIf(t -> now - t >= cooldownMs);
        }

        Long lastNotify = lastJoinNotification.get(uuid);
        if (lastNotify != null && (now - lastNotify) < cooldownMs) {
            return;
        }
        lastJoinNotification.put(uuid, now);
        for (String discordId : followerIds) {
            try {
                plugin.getBotManager().getJda().retrieveUserById(discordId).queue(
                        user -> {
                            if (user == null || user.isBot()) return;
                            EmbedBuilder embed = new EmbedBuilder()
                                    .setTitle(name + " just logged in")
                                    .setDescription("**" + name
                                            + "** has just joined the Minecraft server.")
                                    .setColor(0x2ECC71)
                                    .addField("Joined at",
                                            "<t:" + (now / 1000L) + ":F>", false)
                                    .setTimestamp(Instant.ofEpochMilli(now))
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

        UUID target;
        try {
            target = UUID.fromString(id.substring(prefix.length()));
        } catch (IllegalArgumentException e) {
            event.reply("That player is no longer tracked.").setEphemeral(true).queue();
            return;
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(target);
        String name = offline.getName() != null ? offline.getName() : "a player";
        String discordId = event.getUser().getId();

        if (doFollow) {
            follow(target, discordId);
            event.reply("You will now be notified when **" + name
                    + "** joins the server.").setEphemeral(true).queue();
        } else {
            unfollow(target, discordId);
            event.reply("You will no longer be notified when **" + name
                    + "** joins the server.").setEphemeral(true).queue();
        }
    }

    public Button buildFollowButton(UUID target) {
        return Button.success(FOLLOW_BUTTON_ID + ":" + target, "\ud83d\udd14 Follow");
    }

    public Button buildUnfollowButton(UUID target) {
        return Button.secondary(UNFOLLOW_BUTTON_ID + ":" + target, "\ud83d\udd15 Unfollow");
    }
}

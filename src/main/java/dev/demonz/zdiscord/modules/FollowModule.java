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

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.storage.StorageManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
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
import java.util.logging.Level;

/**
 * Manages the "Follow" social feature: Discord users can follow
 * a Minecraft player and receive a DM whenever that player joins
 * the server. The follow relationship is persisted in storage and
 * cached in memory for fast lookups on the join hot path.
 *
 * <p>This module is intentionally cheap on the join event: it only
 * walks the in-memory cache and fires one async DM per follower.
 * If the cached set is empty (no one follows the joining player)
 * the method returns immediately.</p>
 */
public class FollowModule {

    public static final String FOLLOW_BUTTON_ID = "zdiscord:follow";
    public static final String UNFOLLOW_BUTTON_ID = "zdiscord:unfollow";

    private final ZDiscord plugin;

    /**
     * player UUID → set of discord user IDs that follow them.
     * Populated lazily on the first join; updated on every
     * add/remove call. The cache is intentionally not preloaded
     * — most servers will have a small number of follows.
     */
    private final Map<UUID, Set<String>> followers = new HashMap<>();

    public FollowModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // No scheduled task needed — the join listener calls onPlayerJoin().
    }

    public void reload() {
        synchronized (followers) {
            followers.clear();
        }
    }

    public void shutdown() {
        // Storage writes are scheduled by the storage layer; nothing
        // to flush here.
    }

    // ─── State queries ─────────────────────────────────────

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
        // Pull the live set from storage (cheap on MySQL, single
        // file read on YAML) so the result is always consistent
        // even if the in-memory cache was never warmed up by a
        // join event. The cache is only an optimisation, not a
        // source of truth.
        return plugin.getStorageManager().getFollowedPlayers(discordId);
    }

    // ─── State mutations ───────────────────────────────────

    public void follow(UUID playerUUID, String discordId) {
        synchronized (followers) {
            followers.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(discordId);
        }
        plugin.getStorageManager().addFollower(playerUUID, discordId);
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
    }

    // ─── Join dispatch ─────────────────────────────────────

    /**
     * Called from the join listener (off the main thread is fine,
     * but Bukkit is happy to call this on the main thread too).
     * Looks up the followers of the joining player and DMs them
     * in parallel.
     */
    public void onPlayerJoin(Player player) {
        if (!plugin.getConfigManager().getBoolean("follow.enabled", true)) {
            return;
        }
        UUID uuid = player.getUniqueId();
        Set<String> followerIds = loadFollowersFromStorage(uuid);
        if (followerIds.isEmpty()) {
            return;
        }
        // Cache for next time
        synchronized (followers) {
            followers.put(uuid, new HashSet<>(followerIds));
        }

        String name = player.getName();
        long now = System.currentTimeMillis();
        for (String discordId : followerIds) {
            plugin.getPlatformAdapter().runAsync(() -> {
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
            });
        }
    }

    private Set<String> loadFollowersFromStorage(UUID uuid) {
        StorageManager sm = plugin.getStorageManager();
        if (sm == null) {
            return Set.of();
        }
        return sm.getFollowers(uuid);
    }

    // ─── Button handler (called from SlashCommandManager) ─────

    public void handleFollowButton(ButtonInteractionEvent event) {
        // Component ID format: zdiscord:follow:<uuid>  or  zdiscord:unfollow:<uuid>
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

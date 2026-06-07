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
import dev.demonz.zdiscord.util.PlaceholderUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account linking between Minecraft and Discord. Codes are valid for
 * five minutes and may only be used once.
 */
public class LinkModule {

    private static final long LINK_EXPIRY_MS = 5L * 60L * 1000L;

    private final ZDiscord plugin;
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToMc = new ConcurrentHashMap<>();

    public LinkModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadData();

        plugin.getPlatformAdapter().runAsyncTimer(() -> {
            long now = System.currentTimeMillis();
            pendingLinks.entrySet().removeIf(
                    e -> now - e.getValue().createdAt > LINK_EXPIRY_MS);
        }, 600L, 600L);
    }

    public void reload() {
        loadData();
    }

    private void loadData() {
        linkedAccounts.clear();
        discordToMc.clear();
        for (Map.Entry<UUID, String> entry : plugin.getStorageManager().loadLinks().entrySet()) {
            linkedAccounts.put(entry.getKey(), entry.getValue());
            discordToMc.put(entry.getValue(), entry.getKey());
        }
        plugin.getLogger().info("Loaded " + linkedAccounts.size()
                + " linked accounts from " + plugin.getStorageManager().getTypeName());
    }

    /**
     * Generate a link code for a player. Returns null if the player is
     * already linked (in which case the player has been notified).
     */
    public String generateCode(Player player) {
        if (linkedAccounts.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("link-already-linked"));
            return null;
        }
        // 6 characters of a UUID, uppercased — uniqueness is fine for
        // short-lived codes given the 5-minute expiry.
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        pendingLinks.put(code, new PendingLink(player.getUniqueId(), System.currentTimeMillis()));
        return code;
    }

    /**
     * Process a link from Discord. Returns true if the link was
     * successfully established.
     */
    public boolean processLink(String discordId, String discordName, String code) {
        if (code == null) {
            return false;
        }
        PendingLink pending = pendingLinks.remove(code.trim().toUpperCase());
        if (pending == null) {
            return false;
        }
        if (System.currentTimeMillis() - pending.createdAt > LINK_EXPIRY_MS) {
            return false;
        }

        UUID playerUUID = pending.playerUUID;
        linkedAccounts.put(playerUUID, discordId);
        discordToMc.put(discordId, playerUUID);
        plugin.getStorageManager().saveLink(playerUUID, discordId);

        // Apply Discord role if configured
        String roleId = plugin.getConfigManager().getString("linking.linked-role");
        if (roleId != null && !roleId.isEmpty()) {
            try {
                var guild = plugin.getBotManager().getGuild();
                if (guild != null) {
                    var role = guild.getRoleById(roleId);
                    var member = guild.getMemberById(discordId);
                    if (role != null && member != null) {
                        guild.addRoleToMember(member, role).queue();
                    }
                }
            } catch (Exception e) {
                plugin.debug("Failed to assign linked role: " + e.getMessage());
            }
        }

        // Run reward commands and notify the player on the main thread.
        List<String> rewards = plugin.getConfigManager().getStringList("linking.rewards");
        plugin.getPlatformAdapter().runSync(() -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null) {
                return;
            }
            player.sendMessage(plugin.getMessageManager().get(
                    "link-success", "%discord_name%", discordName));
            for (String cmd : rewards) {
                String resolved = PlaceholderUtil.resolve(cmd, player);
                try {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to run link reward command: "
                            + e.getMessage());
                }
            }
        });

        return true;
    }

    public String getDiscordId(UUID playerUUID) {
        return linkedAccounts.get(playerUUID);
    }

    public UUID getPlayerUUID(String discordId) {
        return discordToMc.get(discordId);
    }

    public boolean isLinked(UUID playerUUID) {
        return linkedAccounts.containsKey(playerUUID);
    }

    public void unlink(UUID playerUUID) {
        String discordId = linkedAccounts.remove(playerUUID);
        if (discordId != null) {
            discordToMc.remove(discordId);
        }
        plugin.getStorageManager().removeLink(playerUUID);
    }

    public void shutdown() {
    }

    private static final class PendingLink {
        final UUID playerUUID;
        final long createdAt;

        PendingLink(UUID playerUUID, long createdAt) {
            this.playerUUID = playerUUID;
            this.createdAt = createdAt;
        }
    }
}

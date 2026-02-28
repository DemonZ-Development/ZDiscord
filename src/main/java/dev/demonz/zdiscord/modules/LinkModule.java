package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Account linking module.
 * Links Discord accounts to Minecraft accounts with reward system.
 * Uses StorageManager for persistent data (YAML or MySQL).
 */
public class LinkModule {

    private final ZDiscord plugin;

    // Maps: link code → {player UUID, creation timestamp}
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();
    // Maps: player UUID → discord ID
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();
    // Maps: discord ID → player UUID
    private final Map<String, UUID> discordToMc = new ConcurrentHashMap<>();

    private static final long LINK_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    public LinkModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadData();

        // Expire pending links every 30 seconds
        plugin.getPlatformAdapter().runAsyncTimer(() -> {
            long now = System.currentTimeMillis();
            pendingLinks.entrySet().removeIf(entry -> now - entry.getValue().createdAt > LINK_EXPIRY_MS);
        }, 600L, 600L);
    }

    public void reload() {
        // Re-load from storage in case data changed externally
        loadData();
    }

    private void loadData() {
        linkedAccounts.clear();
        discordToMc.clear();

        Map<UUID, String> loaded = plugin.getStorageManager().loadLinks();
        for (Map.Entry<UUID, String> entry : loaded.entrySet()) {
            linkedAccounts.put(entry.getKey(), entry.getValue());
            discordToMc.put(entry.getValue(), entry.getKey());
        }
        plugin.getLogger().info("Loaded " + linkedAccounts.size() + " linked accounts from "
                + plugin.getStorageManager().getTypeName());
    }

    /**
     * Generate a link code for a player.
     */
    public String generateCode(Player player) {
        // Check if already linked
        if (linkedAccounts.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("link-already-linked"));
            return null;
        }

        // Generate 6-character code
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        pendingLinks.put(code, new PendingLink(player.getUniqueId(), System.currentTimeMillis()));

        return code;
    }

    /**
     * Process a link from Discord.
     *
     * @return true if link was successful
     */
    public boolean processLink(String discordId, String discordName, String code) {
        PendingLink pending = pendingLinks.remove(code.toUpperCase());
        if (pending == null)
            return false;

        // Check if expired
        if (System.currentTimeMillis() - pending.createdAt > LINK_EXPIRY_MS) {
            return false;
        }

        UUID playerUUID = pending.playerUUID;

        // Store link
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

        // Give rewards
        plugin.getPlatformAdapter().runSync(() -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null) {
                player.sendMessage(plugin.getMessageManager().get("link-success", "%discord_name%", discordName));

                // Execute reward commands
                for (String cmd : plugin.getConfigManager().getStringList("linking.rewards")) {
                    try {
                        String command = cmd.replace("%player%", player.getName());
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                    } catch (Exception e) {
                        plugin.getLogger().warning("Failed to execute link reward command: " + e.getMessage());
                    }
                }
            }
        });

        return true;
    }

    /**
     * Get the Discord ID linked to a player UUID.
     */
    public String getDiscordId(UUID playerUUID) {
        return linkedAccounts.get(playerUUID);
    }

    /**
     * Get the player UUID linked to a Discord ID.
     */
    public UUID getPlayerUUID(String discordId) {
        return discordToMc.get(discordId);
    }

    /**
     * Check if a player is linked.
     */
    public boolean isLinked(UUID playerUUID) {
        return linkedAccounts.containsKey(playerUUID);
    }

    /**
     * Unlink a player.
     */
    public void unlink(UUID playerUUID) {
        String discordId = linkedAccounts.remove(playerUUID);
        if (discordId != null) {
            discordToMc.remove(discordId);
        }
        plugin.getStorageManager().removeLink(playerUUID);
    }

    public void shutdown() {
        // Data is saved on each operation - nothing large to flush
    }

    /**
     * Internal record for tracking pending link codes with timestamps.
     */
    private static class PendingLink {
        final UUID playerUUID;
        final long createdAt;

        PendingLink(UUID playerUUID, long createdAt) {
            this.playerUUID = playerUUID;
            this.createdAt = createdAt;
        }
    }
}

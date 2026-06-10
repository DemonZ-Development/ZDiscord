package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.events.ZDiscordPlayerLinkEvent;
import dev.demonz.zdiscord.util.PlaceholderUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;


public class LinkModule {

    private static final long LINK_EXPIRY_MS = 5L * 60L * 1000L;

    private final ZDiscord plugin;
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToMc = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public LinkModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        loadData();

        plugin.getPlatformAdapter().runAsyncTimer(() -> {
            if (!running) return;
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


    public String generateCode(Player player) {
        if (linkedAccounts.containsKey(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("link-already-linked"));
            return null;
        }


        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        pendingLinks.put(code, new PendingLink(player.getUniqueId(), System.currentTimeMillis()));
        return code;
    }


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

        Bukkit.getPluginManager().callEvent(
                new ZDiscordPlayerLinkEvent(playerUUID, discordId, true));


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
        if (discordId != null) {
            Bukkit.getPluginManager().callEvent(
                    new ZDiscordPlayerLinkEvent(playerUUID, discordId, false));
        }
    }

    public void shutdown() {
        running = false;
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

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.api.events.ZDiscordPlayerLinkEvent;
import dev.demonz.zdiscord.util.PlaceholderUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class LinkModule {

    private final ZDiscord plugin;
    private final AccountLinks links;
    private volatile boolean running = true;
    private dev.demonz.zdiscord.platform.PlatformAdapter.TaskHandle timer;

    public LinkModule(ZDiscord plugin) {
        this.plugin = plugin;
        this.links = new AccountLinks(plugin.getStorageManager());
    }

    public void init() {
        running = true;
        plugin.getLogger().info("Loaded " + links.size() + " linked accounts from "
                + plugin.getStorageManager().getTypeName());

        timer = plugin.getPlatformAdapter().scheduleAsyncTimer(() -> {
            if (!running) return;
            links.expire();
        }, 600L, 600L);
    }

    public void reload() {
        links.expire();
    }

    public String generateCode(Player player) {
        String code = running ? links.code(player.getUniqueId(), false) : null;
        if (code == null) {
            player.sendMessage(plugin.getMessageManager().get("link-already-linked"));
            return null;
        }

        return code;
    }

    public String getLoginCode(UUID playerUUID) {
        return running ? links.code(playerUUID, true) : null;
    }

    public boolean processLink(String discordId, String discordName, String code) {
        if (!running) return false;
        AccountLinks.Change change = links.redeem(discordId, code);
        if (change == null) return false;
        UUID playerUUID = change.player();
        UUID previousPlayer = change.previousPlayer();
        if (previousPlayer != null && !previousPlayer.equals(playerUUID)) {
            plugin.getLogger().info("Discord account " + discordId
                    + " was relinked from " + previousPlayer + " to " + playerUUID);
        }
        plugin.getLogger().info("Linked account: " + playerUUID + " <-> Discord "
                + discordId + " (" + discordName + ")");

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
            plugin.getPlatformAdapter().runForEntity(player, () -> {
                player.sendMessage(plugin.getMessageManager().get(
                        "link-success", "%discord_name%", discordName));
                List<String> resolvedRewards = rewards.stream()
                        .map(cmd -> PlaceholderUtil.resolve(cmd, player))
                        .toList();
                plugin.getPlatformAdapter().runSync(() -> {
                    for (String resolved : resolvedRewards) {
                        try {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
                        } catch (Exception e) {
                            plugin.getLogger().warning("Failed to run link reward command: "
                                    + e.getMessage());
                        }
                    }
                });
            });
        });

        return true;
    }

    public String getDiscordId(UUID playerUUID) {
        return links.discord(playerUUID);
    }

    public UUID getPlayerUUID(String discordId) {
        return links.player(discordId);
    }

    public boolean isLinked(UUID playerUUID) {
        return links.discord(playerUUID) != null;
    }

    public void unlink(UUID playerUUID) {
        String discordId = links.unlink(playerUUID);
        plugin.getLogger().info("Unlinked account: " + playerUUID
                + (discordId != null ? " (Discord " + discordId + ")" : ""));
        if (discordId != null) {
            Bukkit.getPluginManager().callEvent(
                    new ZDiscordPlayerLinkEvent(playerUUID, discordId, false));
        }
    }

    public void shutdown() {
        running = false;
        if (timer != null) timer.cancel();
    }

}

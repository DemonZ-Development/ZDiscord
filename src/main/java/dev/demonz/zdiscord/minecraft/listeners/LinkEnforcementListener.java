package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Enforces account linking — kicks unlinked players when linking.required is
 * true.
 */
public class LinkEnforcementListener implements Listener {

    private final ZDiscord plugin;

    public LinkEnforcementListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (plugin.getLinkModule() == null)
            return;
        if (!plugin.getConfigManager().getBoolean("linking.required", false))
            return;

        // Bypass for ops or players with bypass permission
        if (event.getPlayer().isOp() || event.getPlayer().hasPermission("zdiscord.bypass.link"))
            return;

        if (!plugin.getLinkModule().isLinked(event.getPlayer().getUniqueId())) {
            String kickMsg = plugin.getConfigManager().getString("linking.kick-message",
                    "§c§lZDiscord §8» §fYou must link your Discord account to play!\n\n§7Use §b/zdiscord link §7in-game after an admin whitelists you,\n§7or ask in our Discord for help.");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMsg);
        }
    }
}

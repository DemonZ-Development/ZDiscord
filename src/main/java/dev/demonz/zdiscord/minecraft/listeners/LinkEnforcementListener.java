package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;


public class LinkEnforcementListener implements Listener {

    private final ZDiscord plugin;

    public LinkEnforcementListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (plugin.getLinkModule() == null) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("linking.required", false)) {
            return;
        }

        if (event.getPlayer().isOp()
                || event.getPlayer().hasPermission("zdiscord.bypass.link")) {
            return;
        }

        if (!plugin.getLinkModule().isLinked(event.getPlayer().getUniqueId())) {
            String kickMsg = plugin.getMessageManager().get("link-required");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMsg);
        }
    }
}

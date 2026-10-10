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
    @SuppressWarnings("deprecation")
    public void onLogin(PlayerLoginEvent event) {
        if (plugin.getLinkModule() == null) return;
        if (!plugin.getConfigManager().getBoolean("linking.required", false)) return;

        if (event.getPlayer().isOp()
                || event.getPlayer().hasPermission("zdiscord.bypass.link")) {
            return;
        }

        if (!plugin.getLinkModule().isLinked(event.getPlayer().getUniqueId())) {
            String code = plugin.getLinkModule().getLoginCode(event.getPlayer().getUniqueId());
            if (code == null) return;
            String kickMsg = plugin.getMessageManager().get("link-login-required", "%code%", code);
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMsg);
        }
    }
}

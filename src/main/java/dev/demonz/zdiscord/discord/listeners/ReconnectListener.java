package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;


public class ReconnectListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public ReconnectListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        plugin.getLogger().info("Discord session reconnected; re-validating modules.");

        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                plugin.getBotManager().updateActivity();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update bot activity after reconnect: "
                        + e.getMessage());
            }
        });
    }
}

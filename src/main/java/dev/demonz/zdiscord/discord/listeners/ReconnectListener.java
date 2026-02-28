package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Handles JDA session reconnections.
 * When the bot reconnects after a temporary Discord outage, this listener
 * re-validates module state and updates bot activity.
 */
public class ReconnectListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public ReconnectListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        plugin.getLogger().info("Discord session reconnected — re-validating modules...");

        // Update bot activity
        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                plugin.getBotManager().updateActivity();
                plugin.getLogger().info("Bot activity restored after reconnection.");
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update bot activity after reconnect: " + e.getMessage());
            }
        });
    }
}

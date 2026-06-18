package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;


public class DiscordReactionListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public DiscordReactionListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }
        if (plugin.getReactionRoleModule() != null) {
            plugin.getReactionRoleModule().onReactionAdd(event);
        }
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }
        if (plugin.getReactionRoleModule() != null) {
            plugin.getReactionRoleModule().onReactionRemove(event);
        }
    }
}

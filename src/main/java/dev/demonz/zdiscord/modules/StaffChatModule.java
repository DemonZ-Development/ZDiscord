package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Bidirectional staff chat bridge between in-game and Discord.
 */
public class StaffChatModule extends ListenerAdapter {

    private final ZDiscord plugin;
    private String channelId;
    private final Set<UUID> toggledPlayers = new HashSet<>();

    public StaffChatModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        channelId = plugin.getConfigManager().getString("staff-chat.channel", "");

        if (!channelId.isEmpty() && plugin.getBotManager().isConnected()) {
            plugin.getBotManager().getJda().addEventListener(this);
        }
    }

    /**
     * Send a message from in-game to the staff chat Discord channel.
     */
    public void sendToDiscord(Player player, String message) {
        if (channelId.isEmpty() || !plugin.getBotManager().isConnected())
            return;

        var channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage("**[SC]** " + player.getName() + " » " + message).queue();
        }
    }

    /**
     * Discord → in-game staff chat.
     */
    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot())
            return;
        if (!event.getChannel().getId().equals(channelId))
            return;

        String name = event.getMember() != null ? event.getMember().getEffectiveName() : event.getAuthor().getName();
        String msg = event.getMessage().getContentDisplay();

        plugin.getPlatformAdapter().runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("zdiscord.staffchat")) {
                    player.sendMessage("§3§l[SC] §9" + name + " §8» §f" + msg);
                }
            }
        });
    }

    /**
     * Broadcast a staff chat message to all online staff.
     */
    public void broadcastToStaff(Player sender, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("zdiscord.staffchat")) {
                player.sendMessage("§3§l[SC] §b" + sender.getName() + " §8» §f" + message);
            }
        }
    }

    public boolean isToggled(UUID uuid) {
        return toggledPlayers.contains(uuid);
    }

    public void toggle(UUID uuid) {
        if (!toggledPlayers.remove(uuid)) {
            toggledPlayers.add(uuid);
        }
    }

    public String getChannelId() {
        return channelId;
    }

    public void reload() {
        channelId = plugin.getConfigManager().getString("staff-chat.channel", "");
    }

    public void shutdown() {
        toggledPlayers.clear();
        if (plugin.getBotManager().isConnected()) {
            plugin.getBotManager().getJda().removeEventListener(this);
        }
    }
}

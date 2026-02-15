package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.awt.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Listens for player join/quit events and sends them to Discord.
 */
public class JoinQuitListener implements Listener {

    private final ZDiscord plugin;
    // Track join timestamps for playtime calculation
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    public JoinQuitListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getBotManager().isConnected())
            return;
        if (!plugin.getConfigManager().getBoolean("events.join.enabled", true))
            return;

        Player player = event.getPlayer();

        // Anti-raid check
        if (plugin.getAntiRaidModule() != null) {
            plugin.getAntiRaidModule().onPlayerJoin(player);
        }

        // Track join time for playtime stat
        joinTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

        // Update bot activity
        plugin.getPlatformAdapter().runAsync(() -> plugin.getBotManager().updateActivity());

        // Send event to Discord
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null)
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        if (channel == null)
            return;

        String message = plugin.getConfigManager().getString("events.join.message", "**%player%** joined the server")
                .replace("%player%", player.getName());
        String colorHex = plugin.getConfigManager().getString("events.join.color", "#2ECC71");

        String avatarUrl = "https://minotar.net/helm/" + player.getName() + "/128.png";

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(message, null, avatarUrl)
                .setColor(Color.decode(colorHex))
                .setFooter("Players online: " + plugin.getServer().getOnlinePlayers().size() + "/"
                        + plugin.getServer().getMaxPlayers())
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getBotManager().isConnected())
            return;
        if (!plugin.getConfigManager().getBoolean("events.quit.enabled", true))
            return;

        Player player = event.getPlayer();

        // Update bot activity (delayed to get accurate count)
        plugin.getPlatformAdapter().runLater(
                () -> plugin.getPlatformAdapter().runAsync(() -> plugin.getBotManager().updateActivity()), 20L);

        // Track playtime
        Long joinTime = joinTimestamps.remove(player.getUniqueId());
        if (joinTime != null && plugin.getLeaderboardModule() != null) {
            long sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000;
            if (sessionSeconds > 0) {
                plugin.getLeaderboardModule().incrementStatBy(player.getUniqueId(), "playtime", sessionSeconds);
            }
        }

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null)
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        if (channel == null)
            return;

        String message = plugin.getConfigManager().getString("events.quit.message", "**%player%** left the server")
                .replace("%player%", player.getName());
        String colorHex = plugin.getConfigManager().getString("events.quit.color", "#E74C3C");

        String avatarUrl = "https://minotar.net/helm/" + player.getName() + "/128.png";

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(message, null, avatarUrl)
                .setColor(Color.decode(colorHex))
                .setFooter("Players online: " + (plugin.getServer().getOnlinePlayers().size() - 1) + "/"
                        + plugin.getServer().getMaxPlayers())
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }
}

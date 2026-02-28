package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.awt.*;
import java.time.Instant;

/**
 * Listens for player death events and sends them to Discord.
 */
public class DeathListener implements Listener {

    private final ZDiscord plugin;

    public DeathListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getBotManager().isConnected())
            return;
        if (!plugin.getConfigManager().getBoolean("events.death.enabled", true))
            return;

        Player player = event.getEntity();
        String deathMessage = event.getDeathMessage();
        if (deathMessage == null)
            deathMessage = player.getName() + " died";

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null)
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        if (channel == null)
            return;

        String message = plugin.getConfigManager().getString("events.death.message", "💀 %death_message%")
                .replace("%death_message%", deathMessage)
                .replace("%player%", player.getName());
        String colorHex = plugin.getConfigManager().getString("events.death.color", "#95A5A6");

        EmbedBuilder embed = new EmbedBuilder()
                .setDescription(message)
                .setColor(Color.decode(colorHex))
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();

        // Update leaderboard stats
        if (plugin.getLeaderboardModule() != null) {
            plugin.getLeaderboardModule().incrementStat(player.getUniqueId(), "deaths");
            if (player.getKiller() != null) {
                plugin.getLeaderboardModule().incrementStat(player.getKiller().getUniqueId(), "kills");
            }
        }
    }
}

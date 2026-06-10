package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.PlaceholderUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.time.Instant;


public class DeathListener implements Listener {

    private final ZDiscord plugin;

    public DeathListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        if (plugin.getLeaderboardModule() != null) {
            plugin.getLeaderboardModule().incrementStat(player.getUniqueId(), "deaths");
            if (player.getKiller() != null) {
                plugin.getLeaderboardModule().incrementStat(
                        player.getKiller().getUniqueId(), "kills");
            }
        }

        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("events.death.enabled", true)) {
            return;
        }

        String deathMessage = event.getDeathMessage();
        if (deathMessage == null) {
            deathMessage = player.getName() + " died";
        }

        TextChannel channel = resolveEventChannel();
        if (channel == null) {
            return;
        }

        String template = plugin.getConfigManager()
                .getString("events.death.message", "%death_message%");
        String message = PlaceholderUtil.resolve(template, player)
                .replace("%death_message%", deathMessage);
        String colorHex = plugin.getConfigManager().getString("events.death.color", "#95A5A6");

        EmbedBuilder embed = new EmbedBuilder()
                .setDescription(message)
                .setColor(ColorUtil.parseHex(colorHex))
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private TextChannel resolveEventChannel() {
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        return channel;
    }
}

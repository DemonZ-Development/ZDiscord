package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.awt.*;
import java.time.Instant;

/**
 * Listens for advancement/achievement events and announces them to Discord.
 */
public class AdvancementListener implements Listener {

    private final ZDiscord plugin;

    public AdvancementListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getBotManager().isConnected())
            return;
        if (!plugin.getConfigManager().getBoolean("events.advancement.enabled", true))
            return;

        Advancement advancement = event.getAdvancement();
        // Skip recipe advancements
        if (advancement.getKey().getKey().startsWith("recipes/"))
            return;

        Player player = event.getPlayer();

        // Get display name - try to get a nice name from the key
        String advancementName = formatAdvancementName(advancement.getKey().getKey());

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.achievements");
        if (channel == null)
            channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null)
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        if (channel == null)
            return;

        String colorHex = plugin.getConfigManager().getString("events.advancement.color", "#F1C40F");
        String avatarUrl = "https://minotar.net/helm/" + player.getName() + "/128.png";

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName() + " earned an advancement!", null, avatarUrl)
                .setTitle("🏆 " + advancementName)
                .setColor(Color.decode(colorHex))
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    /**
     * Convert advancement key to display name.
     * e.g. "story/mine_stone" → "Mine Stone"
     */
    private String formatAdvancementName(String key) {
        // Remove category prefix
        if (key.contains("/")) {
            key = key.substring(key.lastIndexOf("/") + 1);
        }
        // Replace underscores with spaces and capitalize
        String[] words = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (sb.length() > 0)
                sb.append(" ");
            sb.append(word.substring(0, 1).toUpperCase()).append(word.substring(1));
        }
        return sb.toString();
    }
}

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.awt.Color;
import java.time.Instant;
import java.util.List;

/**
 * Logs dangerous commands to a Discord channel.
 */
public class CommandLoggerModule implements Listener {

    private final ZDiscord plugin;
    private List<String> watchedCommands;
    private List<String> criticalCommands;
    private String channelId;

    public CommandLoggerModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        channelId = plugin.getConfigManager().getString("command-logger.channel", "");
        watchedCommands = plugin.getConfigManager().getStringList("command-logger.watched-commands");
        criticalCommands = plugin.getConfigManager().getStringList("command-logger.critical-commands");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (channelId.isEmpty() || !plugin.getBotManager().isConnected())
            return;

        String fullCommand = event.getMessage().substring(1); // Remove leading /
        String baseCommand = fullCommand.split(" ")[0].toLowerCase();

        boolean isCritical = criticalCommands.stream().anyMatch(c -> baseCommand.equals(c.toLowerCase()));
        boolean isWatched = isCritical || watchedCommands.stream().anyMatch(c -> baseCommand.equals(c.toLowerCase()));

        if (!isWatched)
            return;

        String playerName = event.getPlayer().getName();
        Color color = isCritical ? new Color(0xE74C3C) : new Color(0xF39C12);
        String severity = isCritical ? "🔴 CRITICAL" : "🟡 WATCHED";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Command Log")
                .setColor(color)
                .addField("Player", playerName, true)
                .addField("Severity", severity, true)
                .addField("Command", "`/" + fullCommand + "`", false)
                .setFooter("ZDiscord Security")
                .setTimestamp(Instant.now());

        var channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void reload() {
        channelId = plugin.getConfigManager().getString("command-logger.channel", "");
        watchedCommands = plugin.getConfigManager().getStringList("command-logger.watched-commands");
        criticalCommands = plugin.getConfigManager().getStringList("command-logger.critical-commands");
    }

    public void shutdown() {
        // No cleanup needed
    }
}

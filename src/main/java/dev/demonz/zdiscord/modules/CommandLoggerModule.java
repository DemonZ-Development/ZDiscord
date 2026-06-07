/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.time.Instant;
import java.util.List;

/**
 * Logs dangerous command executions to a Discord channel. Commands
 * are matched on their base form (the first token of the message).
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
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadConfig() {
        channelId = plugin.getConfigManager().getString("command-logger.channel", "");
        watchedCommands = plugin.getConfigManager().getStringList("command-logger.watched-commands");
        criticalCommands = plugin.getConfigManager().getStringList("command-logger.critical-commands");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (channelId.isEmpty() || !plugin.getBotManager().isConnected()) {
            return;
        }

        String message = event.getMessage();
        if (message.length() < 2 || message.charAt(0) != '/') {
            return;
        }
        String fullCommand = message.substring(1);
        int space = fullCommand.indexOf(' ');
        String baseCommand = (space == -1 ? fullCommand : fullCommand.substring(0, space)).toLowerCase();

        boolean isCritical = criticalCommands.stream().anyMatch(c -> baseCommand.equals(c.toLowerCase()));
        boolean isWatched = isCritical
                || watchedCommands.stream().anyMatch(c -> baseCommand.equals(c.toLowerCase()));
        if (!isWatched) {
            return;
        }

        String playerName = event.getPlayer().getName();
        int color = isCritical ? 0xE74C3C : 0xF39C12;
        String severity = isCritical ? "CRITICAL" : "WATCHED";

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Command Log")
                .setColor(color)
                .addField("Player", playerName, true)
                .addField("Severity", severity, true)
                .addField("Command", "`/" + fullCommand + "`", false)
                .setFooter("ZDiscord security")
                .setTimestamp(Instant.now());

        var channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessageEmbeds(embed.build()).queue();
        }
    }

    public void reload() {
        loadConfig();
    }

    public void shutdown() {
    }
}

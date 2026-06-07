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

/**
 * Forwards player death messages to Discord and increments kill/death
 * statistics for the leaderboard.
 */
public class DeathListener implements Listener {

    private final ZDiscord plugin;

    public DeathListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("events.death.enabled", true)) {
            return;
        }

        Player player = event.getEntity();
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

        if (plugin.getLeaderboardModule() != null) {
            plugin.getLeaderboardModule().incrementStat(player.getUniqueId(), "deaths");
            if (player.getKiller() != null) {
                plugin.getLeaderboardModule().incrementStat(
                        player.getKiller().getUniqueId(), "kills");
            }
        }
    }

    private TextChannel resolveEventChannel() {
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        return channel;
    }
}

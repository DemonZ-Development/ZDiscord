/*
 * Copyright 2024 DemonZ Development
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
import dev.demonz.zdiscord.util.HeadUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.time.Instant;

/**
 * Forwards advancement completions to a Discord channel. Recipe
 * advancements are skipped.
 */
public class AdvancementListener implements Listener {

    private final ZDiscord plugin;

    public AdvancementListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("events.advancement.enabled", true)) {
            return;
        }

        Advancement advancement = event.getAdvancement();
        String key = advancement.getKey().getKey();
        if (key.startsWith("recipes/")) {
            return;
        }

        Player player = event.getPlayer();
        String advancementName = formatAdvancementName(key);

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.achievements");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.events");
        }
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        if (channel == null) {
            return;
        }

        String colorHex = plugin.getConfigManager()
                .getString("events.advancement.color", "#F1C40F");
        String avatarFormat = plugin.getConfigManager()
                .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
        String avatarUrl = HeadUtil.resolve(avatarFormat, player.getUniqueId(), player.getName());

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName() + " earned an advancement", null, avatarUrl)
                .setTitle(advancementName)
                .setColor(ColorUtil.parseHex(colorHex))
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private String formatAdvancementName(String key) {
        if (key.contains("/")) {
            key = key.substring(key.lastIndexOf('/') + 1);
        }
        String[] words = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}

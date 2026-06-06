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
import dev.demonz.zdiscord.util.PlaceholderUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Forwards join/quit events to Discord and tracks per-player session
 * duration for the playtime leaderboard.
 */
public class JoinQuitListener implements Listener {

    private final ZDiscord plugin;
    private final Map<UUID, Long> joinTimestamps = new ConcurrentHashMap<>();

    public JoinQuitListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("events.join.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();

        if (plugin.getAntiRaidModule() != null) {
            plugin.getAntiRaidModule().onPlayerJoin(player);
        }

        joinTimestamps.put(player.getUniqueId(), System.currentTimeMillis());

        plugin.getPlatformAdapter().runAsync(() -> plugin.getBotManager().updateActivity());

        TextChannel channel = resolveEventChannel();
        if (channel == null) {
            return;
        }

        String template = plugin.getConfigManager().getString(
                "events.join.message", "**%player%** joined the server");
        String message = PlaceholderUtil.resolve(template, player);
        String colorHex = plugin.getConfigManager().getString("events.join.color", "#2ECC71");
        String avatarUrl = resolveAvatar(player);

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(message, null, avatarUrl)
                .setColor(ColorUtil.parseHex(colorHex))
                .setFooter("Players online: "
                        + plugin.getServer().getOnlinePlayers().size() + "/"
                        + plugin.getServer().getMaxPlayers())
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("events.quit.enabled", true)) {
            return;
        }

        Player player = event.getPlayer();

        // Defer the activity update and online count by one tick so the
        // player is actually removed from the online list when we read it.
        plugin.getPlatformAdapter().runLater(
                () -> plugin.getPlatformAdapter().runAsync(
                        () -> plugin.getBotManager().updateActivity()),
                2L);

        Long joinTime = joinTimestamps.remove(player.getUniqueId());
        if (joinTime != null && plugin.getLeaderboardModule() != null) {
            long sessionSeconds = (System.currentTimeMillis() - joinTime) / 1000L;
            if (sessionSeconds > 0) {
                plugin.getLeaderboardModule().incrementStatBy(
                        player.getUniqueId(), "playtime", sessionSeconds);
            }
        }

        TextChannel channel = resolveEventChannel();
        if (channel == null) {
            return;
        }

        String template = plugin.getConfigManager().getString(
                "events.quit.message", "**%player%** left the server");
        String message = PlaceholderUtil.resolve(template, player);
        String colorHex = plugin.getConfigManager().getString("events.quit.color", "#E74C3C");
        String avatarUrl = resolveAvatar(player);

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(message, null, avatarUrl)
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

    private String resolveAvatar(Player player) {
        String format = plugin.getConfigManager()
                .getString("chat.avatar-url", "https://crafatar.com/avatars/%uuid%?overlay=true");
        return HeadUtil.resolve(format, player.getUniqueId(), player.getName());
    }
}

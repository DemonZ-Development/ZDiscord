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
import dev.demonz.zdiscord.util.StatusEmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Periodically edits a single Discord message with the current
 * server status.
 */
public class StatusModule {

    private final ZDiscord plugin;
    private String statusMessageId;

    public StatusModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        statusMessageId = loadMessageId();

        int interval = plugin.getConfigManager().getInt("status.update-interval", 30);
        long ticks = interval * 20L;

        plugin.getPlatformAdapter().runAsyncTimer(this::updateStatus, 100L, ticks);
    }

    private void updateStatus() {
        String channelId = plugin.getConfigManager().getString("channels.status", "");
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
            return;
        }
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.status");
        if (channel == null) {
            return;
        }

        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                plugin.getBotManager()::getGuild,
                plugin.getConfigManager().getString("status.embed.title", "Server Status"),
                plugin.getConfigManager().getString("status.embed.color", "#5865F2"),
                plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com"),
                plugin.getConfigManager().getInt("status.update-interval", 30),
                plugin.getConfigManager().getBoolean("status.embed.show-players", true),
                plugin.getConfigManager().getBoolean("status.embed.show-tps", true),
                plugin.getConfigManager().getBoolean("status.embed.show-memory", true),
                plugin.getConfigManager().getDouble("performance.tps-warning", 18.0),
                plugin.getConfigManager().getDouble("performance.tps-critical", 15.0));

        if (statusMessageId != null && !statusMessageId.isEmpty()) {
            channel.editMessageEmbedsById(statusMessageId, StatusEmbedBuilder.build(ctx)).queue(
                    success -> { },
                    error -> {
                        plugin.debug("Status message " + statusMessageId
                                + " is missing, creating a new one.");
                        statusMessageId = null;
                        sendNewStatus(channel, ctx);
                    });
        } else {
            sendNewStatus(channel, ctx);
        }
    }

    private void sendNewStatus(TextChannel channel, StatusEmbedBuilder.StatusContext ctx) {
        channel.sendMessageEmbeds(StatusEmbedBuilder.build(ctx)).queue(
                message -> {
                    if (message != null) {
                        statusMessageId = message.getId();
                        persistMessageId(statusMessageId);
                    }
                },
                error -> plugin.debug("Failed to send status embed: " + error.getMessage()));
    }

    private File dataFile() {
        return new File(plugin.getDataFolder(), "status_data.yml");
    }

    private void persistMessageId(String messageId) {
        try {
            File f = dataFile();
            if (!f.exists()) {
                f.getParentFile().mkdirs();
                f.createNewFile();
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            cfg.set("status-message-id", messageId);
            cfg.save(f);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING,
                    "Failed to save status message ID: " + e.getMessage(), e);
        }
    }

    private String loadMessageId() {
        try {
            File f = dataFile();
            if (!f.exists()) {
                return null;
            }
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(f);
            return cfg.getString("status-message-id", null);
        } catch (Exception e) {
            plugin.debug("Failed to load status message ID: " + e.getMessage());
            return null;
        }
    }

    public void reload() {
        statusMessageId = loadMessageId();
    }

    public void shutdown() {
        if (statusMessageId == null || statusMessageId.isEmpty()) {
            return;
        }
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.status");
        if (channel == null) {
            return;
        }

        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                plugin.getBotManager()::getGuild,
                plugin.getConfigManager().getString("status.embed.title", "Server Status"),
                "#E74C3C",
                plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com"),
                plugin.getConfigManager().getInt("status.update-interval", 30),
                false, false, false,
                18.0, 15.0);
        ctx.online = false;
        ctx.onlineCount = 0;
        ctx.maxCount = BukkitMaxPlayers();

        try {
            channel.editMessageEmbedsById(statusMessageId, StatusEmbedBuilder.build(ctx)).complete();
        } catch (Exception e) {
            plugin.debug("Failed to send offline status: " + e.getMessage());
        }
    }

    private static int BukkitMaxPlayers() {
        try {
            return org.bukkit.Bukkit.getMaxPlayers();
        } catch (Exception e) {
            return 0;
        }
    }
}

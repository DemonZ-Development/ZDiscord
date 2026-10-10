package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.StatusEmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

public class StatusModule {

    private final ZDiscord plugin;
    private dev.demonz.zdiscord.discord.PanelMessage panel;
    private dev.demonz.zdiscord.platform.PlatformAdapter.TaskHandle timer;
    private volatile boolean running = true;

    public StatusModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        panel = new dev.demonz.zdiscord.discord.PanelMessage(loadMessageId(), this::persistMessageId, plugin::debug);
        schedule();
    }

    private void schedule() {
        if (timer != null) timer.cancel();
        running = true;
        long interval = Math.max(2, plugin.getConfigManager().getInt("status.update-interval", 60));
        timer = plugin.getPlatformAdapter().scheduleTimer(this::updateStatus, 100L, interval * 20L);
    }

    private void updateStatus() {
        if (!running) return;
        String channelId = plugin.getConfigManager().getString("channels.status", "");
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
            return;
        }
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.status");
        if (channel == null) {
            return;
        }

        StatusEmbedBuilder.StatusContext ctx = captureContext(
                plugin.getConfigManager().getString("status.embed.color", "#5865F2"));
        panel.update(channel, java.util.List.of(StatusEmbedBuilder.build(ctx)));
    }

    private StatusEmbedBuilder.StatusContext captureContext(String color) {
        return StatusEmbedBuilder.StatusContext.capture(
                plugin.getBotManager()::getGuild,
                plugin.getConfigManager().getString("status.embed.title", "Server Status"),
                color,
                plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com"),
                plugin.getConfigManager().getInt("status.update-interval", 30),
                plugin.getConfigManager().getBoolean("status.embed.show-players", true),
                plugin.getConfigManager().getBoolean("status.embed.show-tps", true),
                plugin.getConfigManager().getBoolean("status.embed.show-memory", true),
                plugin.getConfigManager().getDouble("performance.tps-warning", 18.0),
                plugin.getConfigManager().getDouble("performance.tps-critical", 15.0));
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
        schedule();
    }

    public void shutdown() {
        running = false;
        if (timer != null) timer.cancel();
        if (panel != null) panel.stop();
        String statusMessageId = panel == null ? null : panel.messageId();
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
        ctx.maxCount = maxPlayers();

        try {
            channel.editMessageEmbedsById(statusMessageId, StatusEmbedBuilder.build(ctx)).queue(
                    null,
                    e -> plugin.debug("Failed to send offline status: " + e.getMessage()));
        } catch (Exception e) {
            plugin.debug("Failed to send offline status: " + e.getMessage());
        }
    }

    private static int maxPlayers() {
        try {
            return Bukkit.getMaxPlayers();
        } catch (Exception e) {
            return 0;
        }
    }
}

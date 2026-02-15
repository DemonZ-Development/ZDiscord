package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.*;
import java.time.Instant;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Anti-raid module.
 * Detects mass-join events and auto-enables server lockdown.
 */
public class AntiRaidModule {

    private final ZDiscord plugin;
    private final Queue<Long> recentJoins = new LinkedList<>();
    private final AtomicBoolean lockdownActive = new AtomicBoolean(false);

    public AntiRaidModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // Cleanup old join timestamps periodically
        plugin.getPlatformAdapter().runAsyncTimer(() -> {
            long timeWindow = plugin.getConfigManager().getInt("anti-raid.time-window", 30) * 1000L;
            long cutoff = System.currentTimeMillis() - timeWindow;
            synchronized (recentJoins) {
                while (!recentJoins.isEmpty() && recentJoins.peek() < cutoff) {
                    recentJoins.poll();
                }
            }
        }, 200L, 200L);
    }

    /**
     * Called when a player joins the server.
     */
    public void onPlayerJoin(Player player) {
        if (player.hasPermission("zdiscord.bypass.antiraid"))
            return;

        // Check lockdown
        if (lockdownActive.get()) {
            if (plugin.getConfigManager().getBoolean("anti-raid.lockdown.kick-new-joins", true)) {
                String kickMsg = plugin.getMessageManager().getRaw("lockdown-kick");
                plugin.getPlatformAdapter().runForEntity(player, () -> player.kickPlayer(kickMsg));
                return;
            }
        }

        // Track join
        long now = System.currentTimeMillis();
        synchronized (recentJoins) {
            recentJoins.add(now);
        }

        int maxJoins = plugin.getConfigManager().getInt("anti-raid.max-joins", 10);
        int timeWindow = plugin.getConfigManager().getInt("anti-raid.time-window", 30);

        // Check if threshold exceeded
        synchronized (recentJoins) {
            long cutoff = now - (timeWindow * 1000L);
            long recentCount = recentJoins.stream().filter(t -> t >= cutoff).count();
            if (recentCount >= maxJoins) {
                enableLockdown();
            }
        }
    }

    /**
     * Enable server lockdown.
     */
    private void enableLockdown() {
        if (lockdownActive.getAndSet(true))
            return; // Already in lockdown

        plugin.getLogger().warning("⚠ ANTI-RAID: Lockdown enabled! Possible raid detected.");

        // Notify in-game
        plugin.getPlatformAdapter()
                .runSync(() -> Bukkit.broadcastMessage(plugin.getMessageManager().get("lockdown-enabled")));

        // Notify on Discord
        if (plugin.getConfigManager().getBoolean("anti-raid.lockdown.notify-staff", true)) {
            TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
            if (channel == null)
                channel = plugin.getBotManager().getTextChannel("channels.chat");
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder()
                        .setTitle("🚨 RAID DETECTED — SERVER LOCKDOWN")
                        .setDescription("A possible raid has been detected. The server is now in lockdown mode.\n" +
                                "New player joins will be kicked automatically.")
                        .setColor(Color.decode("#E74C3C"))
                        .addField("Recent Joins", String.valueOf(recentJoins.size()), true)
                        .setTimestamp(Instant.now());
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        }

        // Auto-lift lockdown
        int autoLift = plugin.getConfigManager().getInt("anti-raid.lockdown.auto-lift", 300);
        if (autoLift > 0) {
            plugin.getPlatformAdapter().runLater(() -> disableLockdown(), autoLift * 20L);
        }
    }

    /**
     * Disable server lockdown.
     */
    private void disableLockdown() {
        if (!lockdownActive.getAndSet(false))
            return;

        plugin.getLogger().info("✓ Anti-raid lockdown lifted.");

        plugin.getPlatformAdapter()
                .runSync(() -> Bukkit.broadcastMessage(plugin.getMessageManager().get("lockdown-disabled")));

        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null)
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        if (channel != null) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("✅ Lockdown Lifted")
                    .setDescription("The server lockdown has been lifted. Normal operations resumed.")
                    .setColor(Color.decode("#2ECC71"))
                    .setTimestamp(Instant.now());
            channel.sendMessageEmbeds(embed.build()).queue();
        }

        // Clear join history
        synchronized (recentJoins) {
            recentJoins.clear();
        }
    }

    /**
     * Toggle lockdown manually.
     */
    public void toggleLockdown(CommandSender sender) {
        if (lockdownActive.get()) {
            disableLockdown();
            sender.sendMessage(plugin.getMessageManager().get("lockdown-disabled"));
        } else {
            enableLockdown();
            sender.sendMessage(plugin.getMessageManager().get("lockdown-enabled"));
        }
    }

    public boolean isLockdownActive() {
        return lockdownActive.get();
    }

    public void reload() {
        // Config values (max-joins, time-window, etc.) are read on each check
        // so no specific re-read needed — method exists for consistency
    }

    public void shutdown() {
        // Nothing to clean up
    }
}

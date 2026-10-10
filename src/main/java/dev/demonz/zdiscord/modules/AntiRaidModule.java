package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.EmbedUtil;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public class AntiRaidModule {

    private final ZDiscord plugin;
    private final Deque<Long> recentJoins = new ArrayDeque<>();
    private final AtomicBoolean lockdownActive = new AtomicBoolean(false);
    private volatile boolean running = true;
    private dev.demonz.zdiscord.platform.PlatformAdapter.TaskHandle timer;
    private dev.demonz.zdiscord.platform.PlatformAdapter.TaskHandle autoLiftTask;

    public AntiRaidModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        timer = plugin.getPlatformAdapter().scheduleAsyncTimer(() -> {
            if (!running) return;
            long windowMs = plugin.getConfigManager().getInt("anti-raid.time-window", 30) * 1000L;
            long cutoff = System.currentTimeMillis() - windowMs;
            synchronized (recentJoins) {
                while (!recentJoins.isEmpty() && recentJoins.peekFirst() < cutoff) {
                    recentJoins.pollFirst();
                }
            }
        }, 200L, 200L);
    }

    public void onPlayerJoin(Player player) {
        if (!running) return;
        if (player.hasPermission("zdiscord.bypass.antiraid")) {
            return;
        }

        if (lockdownActive.get()
                && plugin.getConfigManager().getBoolean("anti-raid.lockdown.kick-new-joins", true)) {
            String kickMsg = ColorUtil.colorize(plugin.getConfigManager().getString(
                    "anti-raid.lockdown.kick-message", plugin.getMessageManager().get("lockdown-kick")));
            plugin.getPlatformAdapter().runForEntity(player, () -> player.kickPlayer(kickMsg));
            return;
        }

        long now = System.currentTimeMillis();
        int maxJoins = plugin.getConfigManager().getInt("anti-raid.max-joins", 10);
        int timeWindow = plugin.getConfigManager().getInt("anti-raid.time-window", 30);

        boolean raidDetected;
        synchronized (recentJoins) {
            recentJoins.addLast(now);
            long cutoff = now - (timeWindow * 1000L);
            long recent = recentJoins.stream().filter(t -> t >= cutoff).count();
            raidDetected = recent >= maxJoins;
        }
        if (raidDetected) enableLockdown();
    }

    private synchronized void enableLockdown() {
        if (!running) return;
        if (!lockdownActive.compareAndSet(false, true)) {
            return;
        }
        plugin.getLogger().warning("Anti-raid lockdown enabled; possible raid detected.");

        plugin.getPlatformAdapter().runSync(
                () -> Bukkit.broadcastMessage(plugin.getMessageManager().get("lockdown-enabled")));

        if (plugin.getConfigManager().getBoolean("anti-raid.lockdown.notify-staff", true)) {
            TextChannel channel = resolveEventChannel();
            if (channel != null) {
                int recent;
                synchronized (recentJoins) { recent = recentJoins.size(); }
                channel.sendMessageEmbeds(EmbedUtil.simple(
                        "Raid detected - server lockdown",
                        "A possible raid has been detected. The server is now in lockdown. "
                                + "New joins will be kicked until the lockdown is lifted.",
                        new Color(0xE74C3C))
                        .addField("Recent joins", String.valueOf(recent), true)
                        .build())
                        .queue();
            }
        }

        int autoLift = plugin.getConfigManager().getInt("anti-raid.lockdown.auto-lift", 300);
        if (autoLift > 0) {
            autoLiftTask = plugin.getPlatformAdapter().scheduleLater(this::disableLockdown, autoLift * 20L);
        }
    }

    private synchronized void disableLockdown() {
        if (!running) return;
        cancelAutoLift();
        if (!lockdownActive.compareAndSet(true, false)) {
            return;
        }
        plugin.getLogger().info("Anti-raid lockdown lifted.");

        plugin.getPlatformAdapter().runSync(
                () -> Bukkit.broadcastMessage(plugin.getMessageManager().get("lockdown-disabled")));

        TextChannel channel = resolveEventChannel();
        if (channel != null) {
            channel.sendMessageEmbeds(EmbedUtil.simple(
                    "Lockdown lifted",
                    "The server lockdown has been lifted. Normal operations resumed.",
                    new Color(0x2ECC71)).build()).queue();
        }

        synchronized (recentJoins) {
            recentJoins.clear();
        }
    }

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

    public synchronized void shutdown() {
        running = false;
        if (timer != null) timer.cancel();
        cancelAutoLift();
    }

    private void cancelAutoLift() {
        if (autoLiftTask != null) {
            autoLiftTask.cancel();
            autoLiftTask = null;
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

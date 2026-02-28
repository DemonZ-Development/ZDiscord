package dev.demonz.zdiscord.platform;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

/**
 * Spigot platform adapter using the Bukkit scheduler.
 */
public class SpigotAdapter implements PlatformAdapter {

    private final ZDiscord plugin;

    public SpigotAdapter(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPlatformName() {
        return "Spigot";
    }

    @Override
    public void runAsync(Runnable task) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
    }

    @Override
    public void runSync(Runnable task) {
        Bukkit.getScheduler().runTask(plugin, task);
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        // Spigot doesn't have region-aware scheduling
        runSync(task);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public void runTimer(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
    }

    @Override
    public void cancelAllTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}

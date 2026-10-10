package dev.demonz.zdiscord.platform;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

public class PaperAdapter implements PlatformAdapter {

    private final ZDiscord plugin;

    public PaperAdapter(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPlatformName() {
        return "Paper";
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
        runSync(task);
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
    }

    @Override
    public TaskHandle scheduleLater(Runnable task, long delayTicks) {
        return Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)::cancel;
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
    public TaskHandle scheduleTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, Math.max(1, periodTicks))::cancel;
    }

    @Override
    public TaskHandle scheduleAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, Math.max(1, periodTicks))::cancel;
    }

    @Override
    public void cancelAllTasks() {
        Bukkit.getScheduler().cancelTasks(plugin);
    }
}

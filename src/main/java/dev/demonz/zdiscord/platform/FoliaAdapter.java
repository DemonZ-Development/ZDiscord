package dev.demonz.zdiscord.platform;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;


public class FoliaAdapter implements PlatformAdapter {

    private final ZDiscord plugin;

    public FoliaAdapter(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getPlatformName() {
        return "Folia";
    }

    @Override
    public void runAsync(Runnable task) {
        try {
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            asyncScheduler.getClass().getMethod("runNow", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run());
        } catch (Exception e) {

            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }

    @Override
    public void runSync(Runnable task) {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run());
        } catch (Exception e) {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            scheduler.getClass().getMethod("run", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, Runnable.class)
                    .invoke(scheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run(), null);
        } catch (Exception e) {
            runSync(task);
        }
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("runDelayed", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run(), delayTicks);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks);
        }
    }

    @Override
    public void runTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class)
                    .invoke(globalScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run(), Math.max(1, delayTicks), periodTicks);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        }
    }

    @Override
    public void runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            long delayMs = delayTicks * 50;
            long periodMs = periodTicks * 50;
            asyncScheduler.getClass().getMethod("runAtFixedRate", org.bukkit.plugin.Plugin.class,
                            java.util.function.Consumer.class, long.class, long.class, TimeUnit.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run(),
                            Math.max(1, delayMs), periodMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }

    @Override
    public void cancelAllTasks() {
        try {
            Object globalScheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            globalScheduler.getClass().getMethod("cancelTasks", org.bukkit.plugin.Plugin.class)
                    .invoke(globalScheduler, plugin);
        } catch (Exception e) {
            Bukkit.getScheduler().cancelTasks(plugin);
        }
    }
}

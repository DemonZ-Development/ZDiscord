package dev.demonz.zdiscord.platform;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Folia. The region schedulers live on a different classpath at runtime so
 * everything is done through reflection. Folia must never fall back to the
 * legacy Bukkit scheduler: that scheduler is unsupported there and can move
 * entity work onto the wrong region thread.
 */
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
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            scheduler.getClass().getMethod("runNow", Plugin.class, Consumer.class)
                    .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
        } catch (Exception e) {
            throw schedulerFailure("async run", e);
        }
    }

    @Override
    public void runSync(Runnable task) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("run", Plugin.class, Consumer.class)
                    .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run());
        } catch (Exception e) {
            throw schedulerFailure("global run", e);
        }
    }

    @Override
    public void runForEntity(Entity entity, Runnable task) {
        try {
            Object scheduler = entity.getClass().getMethod("getScheduler").invoke(entity);
            scheduler.getClass().getMethod("run", Plugin.class, Consumer.class, Runnable.class)
                    .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(),
                            (Runnable) () -> plugin.debug("Skipped task for retired entity "
                                    + entity.getUniqueId()));
        } catch (Exception e) {
            throw schedulerFailure("entity run", e);
        }
    }

    @Override
    public void runLater(Runnable task, long delayTicks) {
        scheduleLater(task, delayTicks);
    }

    @Override
    public TaskHandle scheduleLater(Runnable task, long delayTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Object scheduled = scheduler.getClass().getMethod("runDelayed", Plugin.class, Consumer.class, long.class)
                    .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(), Math.max(1, delayTicks));
            return handle(scheduled);
        } catch (Exception e) {
            throw schedulerFailure("delayed global run", e);
        }
    }

    @Override
    public TaskHandle scheduleTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            Object scheduled = scheduler.getClass().getMethod("runAtFixedRate",
                            Plugin.class, Consumer.class, long.class, long.class)
                    .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(),
                            Math.max(1, delayTicks), Math.max(1, periodTicks));
            return handle(scheduled);
        } catch (Exception e) {
            throw schedulerFailure("global timer", e);
        }
    }

    @Override
    public TaskHandle scheduleAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            Object scheduled = scheduler.getClass().getMethod("runAtFixedRate",
                            Plugin.class, Consumer.class, long.class, long.class, TimeUnit.class)
                    .invoke(scheduler, plugin, (Consumer<Object>) t -> task.run(),
                            Math.max(1, delayTicks * 50), Math.max(1, periodTicks) * 50, TimeUnit.MILLISECONDS);
            return handle(scheduled);
        } catch (Exception e) {
            throw schedulerFailure("async timer", e);
        }
    }

    @Override
    public void runTimer(Runnable task, long delayTicks, long periodTicks) {
        scheduleTimer(task, delayTicks, periodTicks);
    }

    @Override
    public void runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        scheduleAsyncTimer(task, delayTicks, periodTicks);
    }

    private TaskHandle handle(Object scheduled) {
        return () -> {
            try {
                scheduled.getClass().getMethod("cancel").invoke(scheduled);
            } catch (Exception e) {
                throw schedulerFailure("cancel task", e);
            }
        };
    }

    @Override
    public void cancelAllTasks() {
        try {
            Object scheduler = Bukkit.class.getMethod("getGlobalRegionScheduler").invoke(null);
            scheduler.getClass().getMethod("cancelTasks", Plugin.class)
                    .invoke(scheduler, plugin);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not cancel Folia global tasks: " + e.getMessage());
        }
        try {
            Object scheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            scheduler.getClass().getMethod("cancelTasks", Plugin.class)
                    .invoke(scheduler, plugin);
        } catch (Exception ignored) {
            plugin.getLogger().warning("Could not cancel Folia async tasks: "
                    + ignored.getMessage());
        }
    }

    private IllegalStateException schedulerFailure(String operation, Exception cause) {
        return new IllegalStateException("Folia scheduler failed during " + operation
                + "; refusing unsafe Bukkit scheduler fallback", cause);
    }
}

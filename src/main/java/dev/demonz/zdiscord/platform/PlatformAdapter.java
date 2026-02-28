package dev.demonz.zdiscord.platform;

import org.bukkit.entity.Entity;

/**
 * Platform abstraction layer for cross-compatibility
 * between Spigot, Paper, and Folia schedulers.
 */
public interface PlatformAdapter {

    /**
     * Get the name of the detected platform.
     */
    String getPlatformName();

    /**
     * Run a task asynchronously.
     */
    void runAsync(Runnable task);

    /**
     * Run a task on the main/region thread.
     */
    void runSync(Runnable task);

    /**
     * Run a task on the region thread for a specific entity (Folia).
     */
    void runForEntity(Entity entity, Runnable task);

    /**
     * Run a task after a delay (in ticks).
     */
    void runLater(Runnable task, long delayTicks);

    /**
     * Run a repeating task (in ticks).
     */
    void runTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Run a repeating async task.
     */
    void runAsyncTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Cancel all tasks owned by the plugin.
     */
    void cancelAllTasks();
}

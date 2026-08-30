package dev.demonz.zdiscord.platform;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Scheduler abstraction so we can run on Spigot, Paper and Folia without
 * littering the rest of the code with instanceof checks.
 */
public interface PlatformAdapter {

    String getPlatformName();

    void runAsync(Runnable task);

    void runSync(Runnable task);

    /**
     * Runs a supplier on the main thread and waits for the result. Callers
     * from the main thread get the value inline; everyone else blocks for at
     * most a few seconds before giving up.
     */
    default <T> T supplySync(Supplier<T> supplier) {
        if (Bukkit.isPrimaryThread()) {
            return supplier.get();
        }
        CompletableFuture<T> done = new CompletableFuture<>();
        runSync(() -> {
            try {
                done.complete(supplier.get());
            } catch (Throwable t) {
                done.completeExceptionally(t);
            }
        });
        try {
            return done.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("sync task did not complete: " + e.getMessage(), e);
        }
    }

    void runForEntity(Entity entity, Runnable task);

    void runLater(Runnable task, long delayTicks);

    void runTimer(Runnable task, long delayTicks, long periodTicks);

    void runAsyncTimer(Runnable task, long delayTicks, long periodTicks);

    void cancelAllTasks();
}

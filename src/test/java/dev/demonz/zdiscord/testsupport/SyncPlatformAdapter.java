package dev.demonz.zdiscord.testsupport;

import dev.demonz.zdiscord.platform.PlatformAdapter;
import org.bukkit.entity.Entity;


public final class SyncPlatformAdapter implements PlatformAdapter {

    public static final SyncPlatformAdapter INSTANCE = new SyncPlatformAdapter();

    private SyncPlatformAdapter() { }

    @Override public String getPlatformName() { return "sync-stub"; }
    @Override public void runAsync(Runnable task) { task.run(); }
    @Override public void runSync(Runnable task) { task.run(); }
    @Override public void runForEntity(Entity entity, Runnable task) { task.run(); }
    @Override public void runLater(Runnable task, long delayTicks) { task.run(); }
    @Override public void runTimer(Runnable task, long delayTicks, long periodTicks) {  }
    @Override public void runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {  }
    @Override public void cancelAllTasks() {  }
}

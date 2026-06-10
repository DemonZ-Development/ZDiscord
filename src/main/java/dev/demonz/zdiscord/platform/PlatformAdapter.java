package dev.demonz.zdiscord.platform;

import org.bukkit.entity.Entity;


public interface PlatformAdapter {


    String getPlatformName();


    void runAsync(Runnable task);


    void runSync(Runnable task);


    void runForEntity(Entity entity, Runnable task);


    void runLater(Runnable task, long delayTicks);


    void runTimer(Runnable task, long delayTicks, long periodTicks);


    void runAsyncTimer(Runnable task, long delayTicks, long periodTicks);


    void cancelAllTasks();
}

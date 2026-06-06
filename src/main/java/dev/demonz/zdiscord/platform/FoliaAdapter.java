/*
 * Copyright 2024 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonz.zdiscord.platform;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;

import java.util.concurrent.TimeUnit;

/**
 * Folia platform adapter using Folia's region-aware scheduling API.
 * All methods use reflection to remain compatible at compile time
 * with Spigot/Paper APIs.
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
            Object asyncScheduler = Bukkit.class.getMethod("getAsyncScheduler").invoke(null);
            asyncScheduler.getClass().getMethod("runNow", org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class)
                    .invoke(asyncScheduler, plugin, (java.util.function.Consumer<Object>) (t) -> task.run());
        } catch (Exception e) {
            // Fallback
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

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

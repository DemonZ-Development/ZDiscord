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

package dev.demonz.zdiscord.testsupport;

import dev.demonz.zdiscord.platform.PlatformAdapter;
import org.bukkit.entity.Entity;

/**
 * A {@link PlatformAdapter} that runs every task synchronously on the
 * caller thread. Suitable for tests that want deterministic flush
 * ordering without spinning up a real server.
 */
public final class SyncPlatformAdapter implements PlatformAdapter {

    public static final SyncPlatformAdapter INSTANCE = new SyncPlatformAdapter();

    private SyncPlatformAdapter() { }

    @Override public String getPlatformName() { return "sync-stub"; }
    @Override public void runAsync(Runnable task) { task.run(); }
    @Override public void runSync(Runnable task) { task.run(); }
    @Override public void runForEntity(Entity entity, Runnable task) { task.run(); }
    @Override public void runLater(Runnable task, long delayTicks) { task.run(); }
    @Override public void runTimer(Runnable task, long delayTicks, long periodTicks) { /* no-op */ }
    @Override public void runAsyncTimer(Runnable task, long delayTicks, long periodTicks) { /* no-op */ }
    @Override public void cancelAllTasks() { /* no-op */ }
}

/*
 * Copyright 2026 DemonZ Development
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

package dev.demonz.zdiscord.util;

import org.bukkit.Bukkit;

/**
 * Wrapper around {@code Bukkit.getTPS()} that returns 20.0 on platforms
 * where the API is not available (Spigot, generic Bukkit).
 *
 * <p>In production this delegates to the static
 * {@link Bukkit#getTPS()} call. In tests it can be redirected via
 * {@link ServerBridge}.</p>
 */
public final class TPSUtil {

    private static final double[] FALLBACK = { 20.0, 20.0, 20.0 };
    private static volatile Boolean tpsAvailable;

    private TPSUtil() {
    }

    public static double[] getTPS() {
        Boolean available = tpsAvailable;
        if (available == null) {
            available = probe();
            tpsAvailable = available;
        }
        if (!available) {
            return FALLBACK.clone();
        }
        try {
            return ServerBridge.tps();
        } catch (NoSuchMethodError | Exception e) {
            tpsAvailable = false;
            return FALLBACK.clone();
        }
    }

    public static boolean isAvailable() {
        Boolean available = tpsAvailable;
        if (available == null) {
            available = probe();
            tpsAvailable = available;
        }
        return available;
    }

    private static boolean probe() {
        try {
            ServerBridge.tps();
            return true;
        } catch (NoSuchMethodError e) {
            return false;
        }
    }
}

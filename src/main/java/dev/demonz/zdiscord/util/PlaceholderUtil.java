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

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Resolves placeholder tokens in user-facing messages.
 *
 * <p>Supported tokens:</p>
 * <ul>
 *   <li>{@code %player%}, {@code %name%} — player username</li>
 *   <li>{@code %displayname%} — player display name (with colour codes stripped)</li>
 *   <li>{@code %uuid%} — player UUID</li>
 *   <li>{@code %world%} — current world name</li>
 *   <li>{@code %online%}, {@code %max%} — server player counts</li>
 *   <li>{@code %x%}, {@code %y%}, {@code %z%} — player coordinates</li>
 *   <li>{@code %health%}, {@code %food%} — vitals</li>
 *   <li>{@code %tps%} — current TPS, one decimal</li>
 * </ul>
 */
public final class PlaceholderUtil {

    private PlaceholderUtil() {
    }

    public static String resolve(String text, Player player) {
        if (text == null) {
            return "";
        }
        text = replaceCommon(text);
        if (player == null) {
            return text;
        }
        text = text.replace("%player%", player.getName())
                .replace("%name%", player.getName())
                .replace("%displayname%", stripColor(player.getDisplayName()))
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%world%", player.getWorld().getName())
                .replace("%x%", String.valueOf(player.getLocation().getBlockX()))
                .replace("%y%", String.valueOf(player.getLocation().getBlockY()))
                .replace("%z%", String.valueOf(player.getLocation().getBlockZ()))
                .replace("%health%", String.valueOf((int) player.getHealth()))
                .replace("%food%", String.valueOf(player.getFoodLevel()));
        return text;
    }

    public static String resolveOffline(String text, OfflinePlayer player) {
        if (text == null) {
            return "";
        }
        text = replaceCommon(text);
        if (player == null) {
            return text;
        }
        String name = player.getName() != null ? player.getName() : "Unknown";
        return text.replace("%player%", name)
                .replace("%name%", name)
                .replace("%uuid%", player.getUniqueId().toString());
    }

    public static String resolveServer(String text) {
        if (text == null) {
            return "";
        }
        return replaceCommon(text);
    }

    private static String replaceCommon(String text) {
        return text
                .replace("%online%", String.valueOf(ServerBridge.onlinePlayers().size()))
                .replace("%max%", String.valueOf(ServerBridge.maxPlayers()))
                .replace("%tps%", String.format("%.1f", TPSUtil.getTPS()[0]));
    }

    private static String stripColor(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");
    }
}

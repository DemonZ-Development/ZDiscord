package dev.demonz.zdiscord.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Utility for resolving placeholders in messages.
 */
public class PlaceholderUtil {

    /**
     * Replace common placeholders in a string.
     */
    public static String resolve(String text, Player player) {
        if (text == null)
            return "";

        text = text.replace("%player%", player.getName());
        text = text.replace("%displayname%", player.getDisplayName());
        text = text.replace("%uuid%", player.getUniqueId().toString());
        text = text.replace("%world%", player.getWorld().getName());
        text = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));

        // Location
        text = text.replace("%x%", String.valueOf(player.getLocation().getBlockX()));
        text = text.replace("%y%", String.valueOf(player.getLocation().getBlockY()));
        text = text.replace("%z%", String.valueOf(player.getLocation().getBlockZ()));

        // Health & food
        text = text.replace("%health%", String.valueOf((int) player.getHealth()));
        text = text.replace("%food%", String.valueOf(player.getFoodLevel()));

        // Server
        text = text.replace("%tps%", String.format("%.1f", dev.demonz.zdiscord.util.TPSUtil.getTPS()[0]));

        return text;
    }

    /**
     * Replace common placeholders without a player context.
     */
    public static String resolveServer(String text) {
        if (text == null)
            return "";

        text = text.replace("%online%", String.valueOf(Bukkit.getOnlinePlayers().size()));
        text = text.replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));
        text = text.replace("%tps%", String.format("%.1f", dev.demonz.zdiscord.util.TPSUtil.getTPS()[0]));

        return text;
    }
}

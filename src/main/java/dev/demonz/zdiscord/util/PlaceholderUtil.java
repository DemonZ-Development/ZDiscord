package dev.demonz.zdiscord.util;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;


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
        return text.replaceAll("Â§[0-9a-fk-or]", "").replaceAll("&[0-9a-fk-or]", "");
    }
}

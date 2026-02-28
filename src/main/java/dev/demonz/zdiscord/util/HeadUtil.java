package dev.demonz.zdiscord.util;

/**
 * Utility for generating player head avatar URLs.
 */
public class HeadUtil {

    /**
     * Get the Crafatar avatar URL for a player UUID.
     */
    public static String getAvatarUrl(String uuid) {
        return "https://crafatar.com/avatars/" + uuid + "?overlay=true";
    }

    /**
     * Get the Crafatar head render URL for a player UUID.
     */
    public static String getHeadUrl(String uuid) {
        return "https://crafatar.com/renders/head/" + uuid + "?overlay=true";
    }

    /**
     * Get the Crafatar body render URL for a player UUID.
     */
    public static String getBodyUrl(String uuid) {
        return "https://crafatar.com/renders/body/" + uuid + "?overlay=true";
    }
}

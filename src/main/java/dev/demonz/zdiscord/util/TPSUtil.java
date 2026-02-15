package dev.demonz.zdiscord.util;

import org.bukkit.Bukkit;

/**
 * Utility for safely retrieving server TPS.
 * Bukkit.getTPS() is a Paper-only API and will throw NoSuchMethodError on
 * Spigot.
 * This wrapper returns a safe fallback value (20.0) when the method is
 * unavailable.
 */
public class TPSUtil {

    private static Boolean tpsAvailable = null;

    /**
     * Get server TPS safely. Returns {20.0, 20.0, 20.0} on Spigot where the API is
     * unavailable.
     */
    public static double[] getTPS() {
        if (tpsAvailable == null) {
            try {
                Bukkit.getTPS();
                tpsAvailable = true;
            } catch (NoSuchMethodError e) {
                tpsAvailable = false;
            }
        }

        if (tpsAvailable) {
            try {
                return Bukkit.getTPS();
            } catch (Exception e) {
                return new double[] { 20.0, 20.0, 20.0 };
            }
        }
        return new double[] { 20.0, 20.0, 20.0 };
    }

    /**
     * Check if TPS API is available (Paper/Folia only).
     */
    public static boolean isAvailable() {
        if (tpsAvailable == null) {
            getTPS(); // trigger check
        }
        return tpsAvailable;
    }
}

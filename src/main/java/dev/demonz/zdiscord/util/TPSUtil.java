package dev.demonz.zdiscord.util;


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

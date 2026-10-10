package dev.demonz.zdiscord.api;

public final class ZDiscordProvider {

    private static volatile ZDiscordAPI instance;

    private ZDiscordProvider() {
    }

    public static ZDiscordAPI get() {
        ZDiscordAPI current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "ZDiscord API is not available. "
                    + "Is the ZDiscord plugin loaded and enabled?");
        }
        return current;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void register(ZDiscordAPI api) {
        instance = api;
    }

    public static void unregister() {
        instance = null;
    }
}

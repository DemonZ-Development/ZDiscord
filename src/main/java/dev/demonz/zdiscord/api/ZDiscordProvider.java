package dev.demonz.zdiscord.api;

public final class ZDiscordProvider {

    private static ZDiscordAPI instance;

    private ZDiscordProvider() {
    }


    public static ZDiscordAPI get() {
        if (instance == null) {
            throw new IllegalStateException(
                    "ZDiscord API is not available. "
                    + "Is the ZDiscord plugin loaded and enabled?");
        }
        return instance;
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

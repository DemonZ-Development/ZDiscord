package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;

import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Real player skin avatars via the SkinsRestorer API when that plugin is
 * installed, falling back to plain mc-heads avatars otherwise. The API is
 * reached through reflection so ZDiscord runs fine without SkinsRestorer.
 */
public final class SkinUtil {

    private static final long CACHE_TTL_MS = 15L * 60L * 1000L;
    private static final int CACHE_MAX_ENTRIES = 2048;
    // Floodgate hands out UUIDs in this fixed range for Bedrock players
    private static final String FLOODGATE_PREFIX = "00000000-0000-0000-0009-";
    private static final String HEAD_URL = "https://mc-heads.net/avatar/%s/%d";

    private static Object playerStorage;
    private static Method getSkinForPlayer;
    private static Method getSkinTextureHash;
    private static java.util.logging.Logger log;
    private static volatile boolean loggedInvokeFailure;
    private static boolean floodgatePresent;

    private static final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private SkinUtil() {
    }

    public static synchronized void init(ZDiscord plugin) {
        playerStorage = null;
        getSkinForPlayer = null;
        getSkinTextureHash = null;
        loggedInvokeFailure = false;
        cache.clear();
        floodgatePresent = classExists("org.geysermc.floodgate.api.FloodgateApi")
                || classExists("org.geysermc.geyser.api.GeyserApi");
        log = plugin != null ? plugin.getLogger() : Logger.getLogger("ZDiscord");
        if (floodgatePresent) {
            log.info("Geyser/Floodgate detected - Bedrock players will get "
                    + "name-based avatars where possible.");
        }

        try {
            Class<?> provider = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object storage = api.getClass().getMethod("getPlayerStorage").invoke(api);
            Method skinForPlayer = storage.getClass().getMethod(
                    "getSkinForPlayer", UUID.class, String.class);

            Class<?> propertyUtils = Class.forName("net.skinsrestorer.api.PropertyUtils");
            Method textureHash = null;
            for (Method m : propertyUtils.getMethods()) {
                if (m.getName().equals("getSkinTextureHash")
                        && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] != String.class) {
                    textureHash = m;
                    break;
                }
            }
            if (textureHash == null) {
                throw new IllegalStateException("unexpected SkinsRestorer API layout");
            }
            playerStorage = storage;
            getSkinForPlayer = skinForPlayer;
            getSkinTextureHash = textureHash;
            log.info("SkinsRestorer detected - using real skins for profile pictures.");
        } catch (ClassNotFoundException e) {
            // not installed, default avatars are fine
        } catch (Exception e) {
            log.warning("SkinsRestorer API is incompatible (" + e.getMessage()
                    + "); falling back to default avatars.");
        }
    }

    public static boolean isAvailable() {
        return playerStorage != null;
    }

    public static boolean isGeyserPresent() {
        return floodgatePresent;
    }

    public static boolean isBedrockUuid(UUID uuid) {
        return uuid != null && uuid.toString().startsWith(FLOODGATE_PREFIX);
    }

    /**
     * Resolves an avatar for a player. A configured format ("auto", empty or
     * null means let ZDiscord pick the best source) always wins over the
     * automatic path.
     */
    public static String resolveAvatar(ZDiscord plugin, String format,
                                       UUID uuid, String name, int size) {
        if (format != null && !format.isEmpty() && !"auto".equals(format)) {
            return HeadUtil.resolve(format, uuid, name);
        }
        return avatar(plugin, uuid, name, size);
    }

    public static String avatar(ZDiscord plugin, UUID uuid, String name, int size) {
        if (uuid == null) {
            return fallbackUrl(null, name, size);
        }
        if (plugin == null
                || playerStorage == null
                || !plugin.getConfigManager().getBoolean("profile.skin-restorer", true)) {
            return fallbackUrl(uuid, name, size);
        }

        long now = System.currentTimeMillis();
        Cached hit = cache.get(uuid);
        if (hit != null && now - hit.fetchedAt < CACHE_TTL_MS) {
            return hit.url != null ? hit.url : fallbackUrl(uuid, name, size);
        }

        String url = fetchSkinUrl(uuid, name, size);
        remember(uuid, url, now);
        return url != null ? url : fallbackUrl(uuid, name, size);
    }

    private static String fallbackUrl(UUID uuid, String name, int size) {
        // Bedrock players have no Mojang account behind their UUID, so ask
        // by name instead and hope mc-heads knows them
        if (name != null && !name.isEmpty() && isBedrockUuid(uuid)) {
            return String.format(Locale.ROOT, HEAD_URL, name, size);
        }
        return HeadUtil.avatar(uuid, size);
    }

    private static void remember(UUID uuid, String url, long now) {
        if (cache.size() >= CACHE_MAX_ENTRIES) {
            evictStale(now);
            evictOverflow();
        }
        cache.put(uuid, new Cached(url, now));
    }

    private static void evictStale(long now) {
        for (Map.Entry<UUID, Cached> entry : cache.entrySet()) {
            if (now - entry.getValue().fetchedAt >= CACHE_TTL_MS) {
                cache.remove(entry.getKey(), entry.getValue());
            }
        }
    }

    private static void evictOverflow() {
        Iterator<Map.Entry<UUID, Cached>> iterator = cache.entrySet().iterator();
        while (cache.size() >= CACHE_MAX_ENTRIES && iterator.hasNext()) {
            Map.Entry<UUID, Cached> entry = iterator.next();
            cache.remove(entry.getKey(), entry.getValue());
        }
    }

    private static String fetchSkinUrl(UUID uuid, String name, int size) {
        try {
            Optional<?> skin = (Optional<?>) getSkinForPlayer.invoke(
                    playerStorage, uuid, name != null ? name : uuid.toString());
            if (skin == null || skin.isEmpty()) {
                return null;
            }
            Object hash = getSkinTextureHash.invoke(null, skin.get());
            if (hash == null || hash.toString().isEmpty()) {
                return null;
            }
            return String.format(Locale.ROOT, HEAD_URL, hash, size);
        } catch (Exception e) {
            if (!loggedInvokeFailure) {
                loggedInvokeFailure = true;
                log.warning("SkinsRestorer skin lookup failed (" + e
                        + "); further lookup failures will be silent.");
            }
            return null;
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static final class Cached {
        final String url;
        final long fetchedAt;

        Cached(String url, long fetchedAt) {
            this.url = url;
            this.fetchedAt = fetchedAt;
        }
    }
}

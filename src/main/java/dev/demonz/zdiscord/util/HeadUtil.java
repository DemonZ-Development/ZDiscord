package dev.demonz.zdiscord.util;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HeadUtil {

    private static final Pattern PLACEHOLDER = Pattern.compile("%(uuid(_nodashes)?|name)%");

    private static final String MC_HEADS_AVATAR = "https://mc-heads.net/avatar/%s/%d";
    private static final String MC_HEADS_BODY = "https://mc-heads.net/body/%s/%d";
    private static final String MC_HEADS_COMBO = "https://mc-heads.net/combo/%s/%d";
    private static final String CRAFATAR_AVATAR = "https://crafatar.com/avatars/%s?overlay=true&size=%d";

    public static final int SIZE_SMALL = 64;
    public static final int SIZE_MEDIUM = 128;
    public static final int SIZE_LARGE = 256;

    private HeadUtil() {
    }

    /**
     * Substitutes %uuid%, %uuid_nodashes% and %name% inside a custom head URL.
     * Falls back to a plain avatar URL when no format is configured.
     */
    public static String resolve(String format, UUID uuid, String name) {
        if (format == null) return avatar(uuid, SIZE_MEDIUM);

        String uuidStr = uuid != null ? uuid.toString() : "";
        String uuidNoDashes = uuidStr.replace("-", "");
        Matcher matcher = PLACEHOLDER.matcher(format);
        StringBuilder out = new StringBuilder();

        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement = switch (token) {
                case "uuid" -> uuidStr;
                case "uuid_nodashes" -> uuidNoDashes;
                default -> name != null ? name : "";
            };
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static String avatar(UUID uuid, int size) {
        return String.format(Locale.ROOT, MC_HEADS_AVATAR, uuid != null ? uuid : "Steve", size);
    }

    public static String avatar(UUID uuid) {
        return avatar(uuid, SIZE_MEDIUM);
    }

    public static String body(UUID uuid, int size) {
        return String.format(Locale.ROOT, MC_HEADS_BODY, uuid, size);
    }

    public static String combo(UUID uuid, int size) {
        return String.format(Locale.ROOT, MC_HEADS_COMBO, uuid, size);
    }

    public static String crafatar(UUID uuid) {
        return String.format(Locale.ROOT, CRAFATAR_AVATAR, uuid, SIZE_MEDIUM);
    }
}

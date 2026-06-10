package dev.demonz.zdiscord.util;

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


    public static String resolve(String format, UUID uuid, String name) {
        if (format == null) {
            return avatar(uuid, SIZE_MEDIUM);
        }
        String uuidStr = uuid.toString();
        String uuidNoDashes = uuidStr.replace("-", "");
        Matcher matcher = PLACEHOLDER.matcher(format);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String token = matcher.group(1);
            String replacement;
            if ("uuid".equals(token)) {
                replacement = uuidStr;
            } else if ("uuid_nodashes".equals(token)) {
                replacement = uuidNoDashes;
            } else {
                replacement = name != null ? name : "";
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }


    public static String avatar(UUID uuid, int size) {
        return String.format(MC_HEADS_AVATAR, uuid.toString(), size);
    }


    public static String avatar(UUID uuid) {
        return avatar(uuid, SIZE_MEDIUM);
    }


    public static String body(UUID uuid, int size) {
        return String.format(MC_HEADS_BODY, uuid.toString(), size);
    }


    public static String combo(UUID uuid, int size) {
        return String.format(MC_HEADS_COMBO, uuid.toString(), size);
    }


    public static String crafatar(UUID uuid) {
        return String.format(CRAFATAR_AVATAR, uuid.toString(), SIZE_MEDIUM);
    }
}

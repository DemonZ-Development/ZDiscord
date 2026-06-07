/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonz.zdiscord.util;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for building player head avatar URLs and resolving the
 * {@code avatar-url} format string from {@code config.yml}.
 *
 * <p>The format string may include any of the following placeholders:</p>
 * <ul>
 *   <li>{@code %uuid%} — player's UUID with dashes</li>
 *   <li>{@code %uuid_nodashes%} — player's UUID without dashes</li>
 *   <li>{@code %name%} — player's username</li>
 * </ul>
 */
public final class HeadUtil {

    private static final Pattern PLACEHOLDER = Pattern.compile("%(uuid(_nodashes)?|name)%");

    private HeadUtil() {
    }

    /**
     * Resolve the configured avatar URL for a player.
     *
     * @param format the format string from {@code config.yml}
     * @param uuid   the player's UUID
     * @param name   the player's username
     * @return the resolved URL, or the input format if it contains no placeholders
     */
    public static String resolve(String format, UUID uuid, String name) {
        if (format == null) {
            return null;
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

    /**
     * Convenience helper: get a Crafatar avatar URL for a player.
     */
    public static String crafatar(UUID uuid) {
        return "https://crafatar.com/avatars/" + uuid + "?overlay=true";
    }
}

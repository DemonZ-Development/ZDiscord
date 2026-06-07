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

import org.bukkit.ChatColor;

import java.awt.Color;

/**
 * Helpers for converting between Minecraft colour codes, hex strings, and
 * AWT {@link Color} instances used by Discord embeds.
 */
public final class ColorUtil {

    private static final Color DEFAULT = new Color(0x5865F2);

    private ColorUtil() {
    }

    /**
     * Parse a hex colour string (#RRGGBB or 0xRRGGBB) to an AWT Color.
     * Returns Discord blurple as a fallback for invalid input.
     */
    public static Color parseHex(String hex) {
        if (hex == null) {
            return DEFAULT;
        }
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }

    /**
     * Format an AWT Color as a #RRGGBB string.
     */
    public static String toHex(Color color) {
        if (color == null) {
            return "#5865F2";
        }
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Strip Minecraft colour codes from a string. Handles both
     * section-sign codes (§x) and ampersand codes (&x), but not
     * hex codes (&amp;#RRGGBB).
     */
    public static String stripColor(String text) {
        if (text == null) {
            return "";
        }
        String stripped = ChatColor.stripColor(text);
        if (stripped == null) {
            return "";
        }
        return stripped.replaceAll("&[0-9a-fk-or]", "");
    }
}

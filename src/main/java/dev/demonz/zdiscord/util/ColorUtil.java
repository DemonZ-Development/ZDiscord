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

    /**
     * Convert Minecraft colour codes to Discord markdown so that
     * text originating in chat templates, welcome messages, and
     * player-supplied fields still carries its formatting when
     * rendered inside a Discord embed. Colour codes themselves
     * have no Discord equivalent, so they are dropped; the four
     * formatting codes are translated:
     *
     * <ul>
     *   <li>{@code &l} → {@code **bold**}</li>
     *   <li>{@code &o} → {@code *italic*}</li>
     *   <li>{@code &n} → {@code __underline__}</li>
     *   <li>{@code &m} → {@code ~~strikethrough~~}</li>
     * </ul>
     *
     * <p>The same translations apply to the section-sign (§)
     * variant. {@code &l &o &n &m} act as toggles (the second
     * occurrence of the same code closes the run, exactly like
     * Minecraft's behaviour). {@code &r} (reset) closes every
     * active run in the right order so the resulting markdown
     * is always balanced. Discord allows
     * {@code **a *b* a**}-style nesting, so opening a new
     * format inside an already-open one does <em>not</em> close
     * the outer run; only {@code &r} or a matching close does.</p>
     */
    public static String toDiscordMarkdown(String text) {
        if (text == null) {
            return "";
        }
        if (text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strike = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                i++; // consume the code
                switch (code) {
                    case 'l':
                        out.append(bold ? "**" : "**");
                        bold = !bold;
                        break;
                    case 'o':
                        out.append(italic ? "*" : "*");
                        italic = !italic;
                        break;
                    case 'n':
                        out.append(underline ? "__" : "__");
                        underline = !underline;
                        break;
                    case 'm':
                        out.append(strike ? "~~" : "~~");
                        strike = !strike;
                        break;
                    case 'k':
                        // Obfuscated has no static equivalent; drop.
                        break;
                    case 'r':
                        // Close every open run in the inverse
                        // order they were opened so the
                        // markdown is balanced.
                        if (strike) out.append("~~");
                        if (underline) out.append("__");
                        if (italic) out.append('*');
                        if (bold) out.append("**");
                        bold = italic = underline = strike = false;
                        break;
                    default:
                        // Colour codes (0-9, a-f) — drop silently.
                        break;
                }
                continue;
            }
            out.append(c);
        }
        // Close any formats that were still open at end of input.
        if (strike) out.append("~~");
        if (underline) out.append("__");
        if (italic) out.append('*');
        if (bold) out.append("**");
        return out.toString();
    }
}

package dev.demonz.zdiscord.util;

import org.bukkit.ChatColor;

import java.awt.Color;

public final class ColorUtil {

    private static final Color DEFAULT = new Color(0x5865F2);

    private ColorUtil() {
    }

    public static Color parseHex(String hex) {
        if (hex == null) return DEFAULT;
        try {
            return Color.decode(hex);
        } catch (NumberFormatException e) {
            return DEFAULT;
        }
    }

    public static String toHex(Color color) {
        if (color == null) return "#5865F2";
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    public static String stripColor(String text) {
        if (text == null) return "";
        String stripped = ChatColor.stripColor(text);
        if (stripped == null) return "";
        return stripped.replaceAll("&[0-9a-fk-orA-FK-OR]", "");
    }

    /**
     * Converts a legacy &amp;-colour string into Discord markdown (bold, italic,
     * underline, strikethrough). Magic formatting (&amp;k) is dropped.
     */
    public static String toDiscordMarkdown(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;

        StringBuilder out = new StringBuilder(text.length());
        boolean bold = false, italic = false, underline = false, strike = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '\u00A7') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(++i));
                switch (code) {
                    case 'l' -> { out.append("**"); bold = !bold; }
                    case 'o' -> { out.append("*"); italic = !italic; }
                    case 'n' -> { out.append("__"); underline = !underline; }
                    case 'm' -> { out.append("~~"); strike = !strike; }
                    case 'r' -> {
                        if (strike) out.append("~~");
                        if (underline) out.append("__");
                        if (italic) out.append('*');
                        if (bold) out.append("**");
                        bold = italic = underline = strike = false;
                    }
                    default -> { }
                }
                continue;
            }
            out.append(c);
        }

        if (strike) out.append("~~");
        if (underline) out.append("__");
        if (italic) out.append('*');
        if (bold) out.append("**");
        return out.toString();
    }
}

package dev.demonz.zdiscord.util;

import org.bukkit.ChatColor;

import java.awt.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public final class ColorUtil {

    private static final Color DEFAULT = new Color(0x5865F2);
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");

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

    public static String colorize(String text) {
        if (text == null || text.isEmpty()) return text == null ? "" : text;
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String hex = matcher.group(1);
            StringBuilder replacement = new StringBuilder("\u00A7x");
            for (char c : hex.toCharArray()) {
                replacement.append('\u00A7').append(Character.toLowerCase(c));
            }
            matcher.appendReplacement(sb, replacement.toString());
        }
        matcher.appendTail(sb);
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
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

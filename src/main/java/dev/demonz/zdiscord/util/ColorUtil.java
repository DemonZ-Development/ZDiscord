package dev.demonz.zdiscord.util;

import org.bukkit.ChatColor;

import java.awt.Color;


public final class ColorUtil {

    private static final Color DEFAULT = new Color(0x5865F2);

    private ColorUtil() {
    }


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


    public static String toHex(Color color) {
        if (color == null) {
            return "#5865F2";
        }
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }


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
            if ((c == '&' || c == 'Â§') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                i++;
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

                        break;
                    case 'r':



                        if (strike) out.append("~~");
                        if (underline) out.append("__");
                        if (italic) out.append('*');
                        if (bold) out.append("**");
                        bold = italic = underline = strike = false;
                        break;
                    default:

                        break;
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

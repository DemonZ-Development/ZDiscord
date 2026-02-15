package dev.demonz.zdiscord.util;

import org.bukkit.ChatColor;

import java.awt.*;

/**
 * Color utility for converting between Minecraft, hex, and AWT colors.
 */
public class ColorUtil {

    /**
     * Parse a hex color string to AWT Color.
     */
    public static Color parseHex(String hex) {
        try {
            return Color.decode(hex);
        } catch (Exception e) {
            return Color.decode("#5865F2"); // Discord blurple default
        }
    }

    /**
     * Convert AWT Color to hex string.
     */
    public static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    /**
     * Strip Minecraft color codes from a string.
     */
    public static String stripColor(String text) {
        if (text == null)
            return "";
        return ChatColor.stripColor(text.replaceAll("&[0-9a-fk-or]", ""));
    }
}

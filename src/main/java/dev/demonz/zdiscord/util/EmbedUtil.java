package dev.demonz.zdiscord.util;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.Color;
import java.time.Instant;

public final class EmbedUtil {

    private static final Color ERROR = new Color(0xE74C3C);
    private static final Color SUCCESS = new Color(0x2ECC71);
    private static final Color INFO = new Color(0x3498DB);
    private static final Color WARN = new Color(0xF39C12);
    private static final String FOOTER = "ZDiscord";

    private EmbedUtil() {
    }

    public static EmbedBuilder simple(String title, String description, Color color) {
        return branded()
                .setTitle(title)
                .setDescription(description)
                .setColor(color);
    }

    public static EmbedBuilder error(String message) {
        return branded()
                .setTitle(":x: Error")
                .setDescription(message)
                .setColor(ERROR);
    }

    public static EmbedBuilder success(String message) {
        return branded()
                .setTitle(":white_check_mark: Success")
                .setDescription(message)
                .setColor(SUCCESS);
    }

    public static EmbedBuilder info(String title, String message) {
        return branded()
                .setTitle(title)
                .setDescription(message)
                .setColor(INFO);
    }

    public static EmbedBuilder warn(String title, String message) {
        return branded()
                .setTitle(":warning: " + title)
                .setDescription(message)
                .setColor(WARN);
    }


    public static EmbedBuilder branded() {
        return new EmbedBuilder()
                .setFooter(FOOTER)
                .setTimestamp(Instant.now());
    }
}

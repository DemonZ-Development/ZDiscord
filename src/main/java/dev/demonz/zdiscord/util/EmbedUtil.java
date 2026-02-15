package dev.demonz.zdiscord.util;

import net.dv8tion.jda.api.EmbedBuilder;

import java.awt.*;
import java.time.Instant;

/**
 * Utility for building Discord embeds quickly.
 */
public class EmbedUtil {

    /**
     * Create a simple embed with a title, description, and color.
     */
    public static EmbedBuilder simple(String title, String description, Color color) {
        return new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setFooter("ZDiscord")
                .setTimestamp(Instant.now());
    }

    /**
     * Create an error embed.
     */
    public static EmbedBuilder error(String message) {
        return new EmbedBuilder()
                .setTitle("❌ Error")
                .setDescription(message)
                .setColor(Color.decode("#E74C3C"))
                .setTimestamp(Instant.now());
    }

    /**
     * Create a success embed.
     */
    public static EmbedBuilder success(String message) {
        return new EmbedBuilder()
                .setTitle("✅ Success")
                .setDescription(message)
                .setColor(Color.decode("#2ECC71"))
                .setTimestamp(Instant.now());
    }

    /**
     * Create an info embed.
     */
    public static EmbedBuilder info(String title, String message) {
        return new EmbedBuilder()
                .setTitle("ℹ️ " + title)
                .setDescription(message)
                .setColor(Color.decode("#3498DB"))
                .setTimestamp(Instant.now());
    }
}

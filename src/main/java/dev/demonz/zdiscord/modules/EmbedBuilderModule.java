package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.command.CommandSender;

import java.awt.*;
import java.time.Instant;

/**
 * Custom embed builder module.
 * Allows admins to build and send custom embeds to Discord channels via in-game
 * commands.
 */
public class EmbedBuilderModule {

    private final ZDiscord plugin;

    public EmbedBuilderModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    /**
     * Create and send a custom embed to the chat channel.
     */
    public void createAndSend(CommandSender sender, String title, String description) {
        createAndSend(sender, title, description, "#5865F2", null);
    }

    /**
     * Create and send a custom embed with color.
     */
    public void createAndSend(CommandSender sender, String title, String description, String colorHex,
            String channelId) {
        TextChannel channel;
        if (channelId != null && !channelId.isEmpty()) {
            channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        } else {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }

        if (channel == null) {
            sender.sendMessage("§cCould not find the target channel.");
            return;
        }

        Color color;
        try {
            color = Color.decode(colorHex);
        } catch (Exception e) {
            color = Color.decode("#5865F2");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setFooter("ZDiscord • Custom Embed")
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> sender.sendMessage(plugin.getMessageManager().get("embed-created",
                        "%channel%", channel.getName())),
                error -> sender.sendMessage("§cFailed to send embed: " + error.getMessage()));
    }

    /**
     * Create and send a rich embed with all fields.
     */
    public void createRichEmbed(CommandSender sender, String title, String description,
            String colorHex, String thumbnail, String image,
            String footer, String channelId) {
        TextChannel channel;
        if (channelId != null && !channelId.isEmpty()) {
            channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        } else {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }

        if (channel == null) {
            sender.sendMessage("§cCould not find the target channel.");
            return;
        }

        Color color;
        try {
            color = Color.decode(colorHex);
        } catch (Exception e) {
            color = Color.decode("#5865F2");
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(color)
                .setTimestamp(Instant.now());

        if (thumbnail != null && !thumbnail.isEmpty())
            embed.setThumbnail(thumbnail);
        if (image != null && !image.isEmpty())
            embed.setImage(image);
        if (footer != null && !footer.isEmpty())
            embed.setFooter(footer);
        else
            embed.setFooter("ZDiscord • Custom Embed");

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> sender.sendMessage(plugin.getMessageManager().get("embed-created",
                        "%channel%", channel.getName())),
                error -> sender.sendMessage("§cFailed to send embed: " + error.getMessage()));
    }

    public void reload() {
        // No config to re-read — exists for consistency with other modules
    }
}

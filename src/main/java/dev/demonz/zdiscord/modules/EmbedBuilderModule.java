package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.command.CommandSender;
import java.time.Instant;


public class EmbedBuilderModule {

    private final ZDiscord plugin;

    public EmbedBuilderModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void createAndSend(CommandSender sender, String title, String description) {
        createAndSend(sender, title, description, null, null);
    }

    public void createAndSend(CommandSender sender, String title, String description,
                              String colorHex, String channelId) {
        TextChannel channel = resolveChannel(channelId);
        if (channel == null) {
            sender.sendMessage("Could not find the target channel.");
            return;
        }
        send(sender, channel, build(title, description, colorHex, null, null, "ZDiscord - Custom Embed"));
    }

    public void createRichEmbed(CommandSender sender, String title, String description,
                                String colorHex, String thumbnail, String image,
                                String footer, String channelId) {
        TextChannel channel = resolveChannel(channelId);
        if (channel == null) {
            sender.sendMessage("Could not find the target channel.");
            return;
        }
        send(sender, channel,
                build(title, description, colorHex, thumbnail, image,
                        footer != null && !footer.isEmpty() ? footer : "ZDiscord - Custom Embed"));
    }

    private EmbedBuilder build(String title, String description, String colorHex,
                               String thumbnail, String image, String footer) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(title)
                .setDescription(description)
                .setColor(ColorUtil.parseHex(colorHex))
                .setFooter(footer)
                .setTimestamp(Instant.now());
        if (thumbnail != null && !thumbnail.isEmpty()) {
            embed.setThumbnail(thumbnail);
        }
        if (image != null && !image.isEmpty()) {
            embed.setImage(image);
        }
        return embed;
    }

    private void send(CommandSender sender, TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build()).queue(
                success -> sender.sendMessage(plugin.getMessageManager().get(
                        "embed-created", "%channel%", channel.getName())),
                error -> sender.sendMessage("Failed to send embed: " + error.getMessage()));
    }

    private TextChannel resolveChannel(String channelId) {
        if (channelId != null && !channelId.isEmpty()) {
            return plugin.getBotManager().getJda() != null
                    ? plugin.getBotManager().getJda().getTextChannelById(channelId)
                    : null;
        }
        return plugin.getBotManager().getTextChannel("channels.chat");
    }

    public void reload() {
    }
}

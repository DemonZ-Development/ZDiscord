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

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.util.List;

/**
 * Sends custom embeds to Discord channels from in-game.
 *
 * <p>The {@code /zdiscord embed} admin command supports a basic
 * title/description form. The richer {@link #createRichEmbed} overload
 * is exposed for the developer API but is not yet bound to a user
 * command.</p>
 */
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

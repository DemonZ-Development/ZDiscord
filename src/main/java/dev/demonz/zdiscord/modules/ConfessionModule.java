package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConfessionModule {

    private static final String CONFESSION_TITLE = "\uD83D\uDC8C A new confession";
    private static final int MAX_MESSAGE_LENGTH = 1500;

    private final ZDiscord plugin;
    private final ConcurrentHashMap<String, Long> cooldowns = new ConcurrentHashMap<>();
    private final AtomicInteger counter = new AtomicInteger(0);

    public ConfessionModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void postFromDiscord(SlashCommandInteractionEvent event, String message) {
        post(
                "discord:" + event.getUser().getId(),
                message,
                reply -> event.reply(reply).setEphemeral(true).queue());
    }

    public void postFromMinecraft(Player player, String message) {
        post(
                "minecraft:" + player.getUniqueId(),
                message,
                reply -> plugin.getPlatformAdapter().runForEntity(
                        player, () -> player.sendMessage(reply)));
    }

    private void post(String cooldownKey, String message, ReplyTarget replyTarget) {
        if (plugin.getBotManager() == null
                || plugin.getBotManager().getJda() == null
                || !plugin.getBotManager().isConnected()) {
            replyTarget.reply("Confessions are unavailable because the Discord bot is not connected.");
            return;
        }

        String channelId = plugin.getConfigManager()
                .getString("channels.confessions", "").trim();
        if (channelId.isEmpty() || channelId.startsWith("YOUR_")) {
            replyTarget.reply("Confessions are disabled on this server "
                    + "(no channels.confessions is configured).");
            return;
        }

        TextChannel channel = plugin.getBotManager().getJda()
                .getTextChannelById(channelId);
        if (channel == null) {
            replyTarget.reply("Confessions are disabled on this server "
                    + "(the configured channel could not be found).");
            return;
        }

        String cleaned = message == null ? "" : message.trim();
        if (cleaned.isEmpty()) {
            replyTarget.reply("Usage: /confess <message>");
            return;
        }
        if (cleaned.length() > MAX_MESSAGE_LENGTH) {
            replyTarget.reply("Your confession is too long (max "
                    + MAX_MESSAGE_LENGTH + " characters).");
            return;
        }

        long cooldownMs = plugin.getConfigManager()
                .getInt("confessions.cooldown", 300) * 1000L;
        long now = System.currentTimeMillis();
        Long lastConfession = cooldowns.get(cooldownKey);
        if (lastConfession != null && (now - lastConfession) < cooldownMs) {
            long remaining = Math.max(1L, (cooldownMs - (now - lastConfession) + 999L) / 1000L);
            replyTarget.reply("You can confess again in " + remaining + " seconds.");
            return;
        }

        String handle = "Confessor #" + counter.incrementAndGet();
        String colorHex = plugin.getConfigManager()
                .getString("confessions.color", "#9B59B6");

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(CONFESSION_TITLE, null, null)
                .setDescription(ColorUtil.toDiscordMarkdown(cleaned))
                .setColor(ColorUtil.parseHex(colorHex))
                .setFooter(handle + "  \u00B7  posted at", null)
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> {
                    cooldowns.put(cooldownKey, now);
                    replyTarget.reply("Your confession was posted anonymously.");
                },
                error -> replyTarget.reply("Failed to post your confession: "
                        + error.getMessage()));
    }

    private interface ReplyTarget {
        void reply(String message);
    }
}

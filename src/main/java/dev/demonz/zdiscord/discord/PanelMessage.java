package dev.demonz.zdiscord.discord;

import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import java.util.List;
import java.util.function.Consumer;

public final class PanelMessage {
    private final Consumer<String> persist;
    private final Consumer<String> log;
    private String messageId;
    private String channelId;
    private long generation;
    private boolean pending;
    private boolean stopped;

    public PanelMessage(String messageId, Consumer<String> persist, Consumer<String> log) {
        this.messageId = messageId;
        this.persist = persist;
        this.log = log;
    }

    public synchronized String messageId() { return messageId; }

    public synchronized void stop() { stopped = true; generation++; pending = false; }

    public synchronized void update(TextChannel channel, List<MessageEmbed> embeds) {
        if (stopped) return;
        if (channelId != null && !channelId.equals(channel.getId())) {
            messageId = null;
            pending = false;
            generation++;
        }
        channelId = channel.getId();
        if (pending) return;
        pending = true;
        long current = generation;
        if (messageId == null || messageId.isBlank()) {
            send(channel, embeds, current);
        } else {
            channel.editMessageEmbedsById(messageId, embeds).queue(
                    success -> finish(current),
                    error -> {
                        synchronized (this) {
                            if (stopped || current != generation) return;
                            if (error instanceof ErrorResponseException response
                                    && response.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                                messageId = null;
                                send(channel, embeds, current);
                            } else {
                                pending = false;
                                log.accept("Panel update failed: " + error.getMessage());
                            }
                        }
                    });
        }
    }

    private void send(TextChannel channel, List<MessageEmbed> embeds, long current) {
        channel.sendMessageEmbeds(embeds).queue(message -> {
            synchronized (this) {
                if (stopped || current != generation) return;
                messageId = message.getId();
                persist.accept(messageId);
                pending = false;
            }
        }, error -> {
            synchronized (this) {
                if (stopped || current != generation) return;
                pending = false;
                log.accept("Panel creation failed: " + error.getMessage());
            }
        });
    }

    private synchronized void finish(long current) {
        if (current == generation) pending = false;
    }
}

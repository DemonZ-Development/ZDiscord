package dev.demonz.zdiscord.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;


public class WebhookManager {

    private static final Pattern FORBIDDEN_NAME = Pattern.compile("(?i)(discord|clyde)");
    private static final int MAX_SENDS_PER_WINDOW = 4;
    private static final long WINDOW_MS = 2_000L;

    private final ZDiscord plugin;
    private final Map<String, WebhookClient> webhookClients = new ConcurrentHashMap<>();
    private final AtomicLong nextSlot = new AtomicLong(0);
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "ZDiscord-WebhookScheduler");
        t.setDaemon(true);
        return t;
    });

    public WebhookManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public WebhookClient getOrCreateWebhook(TextChannel channel) {
        WebhookClient existing = webhookClients.get(channel.getId());
        if (existing != null) {
            return existing;
        }
        try {
            var webhooks = channel.retrieveWebhooks().complete();
            var found = webhooks.stream()
                    .filter(w -> "ZChat".equals(w.getName()))
                    .findFirst();

            String webhookUrl;
            if (found.isPresent()) {
                webhookUrl = found.get().getUrl();
            } else {
                var webhook = channel.createWebhook("ZChat").complete();
                webhookUrl = webhook.getUrl();
            }
            WebhookClient client = WebhookClient.withUrl(webhookUrl);
            webhookClients.put(channel.getId(), client);
            return client;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create webhook for #"
                    + channel.getName() + ": " + e.getMessage());
            return null;
        }
    }


    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "Player";
        }



        String sanitized = FORBIDDEN_NAME.matcher(username).replaceAll("Player");
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        if (sanitized.isEmpty()) {
            sanitized = "Player";
        }
        return sanitized;
    }


    public void sendWebhookMessage(TextChannel channel, String username, String avatarUrl, String message) {
        WebhookClient client = getOrCreateWebhook(channel);
        if (client == null) {
            return;
        }

        String safeName = sanitizeUsername(username);
        long delayMs = acquireSlot();
        WebhookMessageBuilder builder = new WebhookMessageBuilder()
                .setUsername(safeName)
                .setAvatarUrl(avatarUrl)
                .setContent(message);

        if (delayMs == 0) {
            plugin.getPlatformAdapter().runAsync(() -> doSend(client, channel, builder));
        } else {
            scheduler.schedule(
                    () -> plugin.getPlatformAdapter().runAsync(() -> doSend(client, channel, builder)),
                    delayMs, TimeUnit.MILLISECONDS);
        }
    }

    private void doSend(WebhookClient client, TextChannel channel, WebhookMessageBuilder builder) {
        try {
            client.send(builder.build()).get();
        } catch (Exception e) {
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";
            if (errorMsg.contains("404") || errorMsg.contains("Unknown Webhook")) {
                webhookClients.remove(channel.getId());
                plugin.debug("Webhook invalidated for #" + channel.getName()
                        + " â€” will be re-created on next message.");
            } else {
                plugin.debug("Webhook send failed: " + errorMsg);
            }
        }
    }


    private long acquireSlot() {
        long now = System.currentTimeMillis();
        long minInterval = WINDOW_MS / MAX_SENDS_PER_WINDOW;
        while (true) {
            long slot = nextSlot.get();
            long sendAt = Math.max(slot, now);
            if (nextSlot.compareAndSet(slot, sendAt + minInterval)) {
                long delay = sendAt - now;
                return delay > 0 ? delay : 0;
            }
        }
    }

    public void shutdown() {
        scheduler.shutdownNow();
        for (WebhookClient client : webhookClients.values()) {
            if (client != null) {
                try {
                    client.close();
                } catch (Exception ignored) {
                }
            }
        }
        webhookClients.clear();
    }
}

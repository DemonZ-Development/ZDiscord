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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Webhook sending with a global rate-limit gate. Discord only allows ~5
 * webhook messages per 2 seconds per channel, so sends are spaced out
 * via a token-slot counter shared across all channels.
 */
public class WebhookManager {

    private static final Pattern FORBIDDEN_NAME = Pattern.compile("(?i)(discord|clyde)");
    private static final int MAX_SENDS_PER_WINDOW = 4;
    private static final long WINDOW_MS = 2_000L;
    private static final int MAX_PENDING_SENDS = 500;

    private final ZDiscord plugin;
    private final Map<String, WebhookClient> webhookClients = new ConcurrentHashMap<>();
    private final Object createLock = new Object();
    private final AtomicLong nextSlot = new AtomicLong(0);
    private final AtomicInteger pendingSends = new AtomicInteger();
    private volatile boolean running = true;
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, r -> {
        Thread t = new Thread(r, "ZDiscord-WebhookScheduler");
        t.setDaemon(true);
        return t;
    });

    public WebhookManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public WebhookClient getOrCreateWebhook(TextChannel channel) {
        if (!running) return null;
        WebhookClient existing = webhookClients.get(channel.getId());
        if (existing != null) return existing;

        synchronized (createLock) {
            WebhookClient cached = webhookClients.get(channel.getId());
            if (cached != null) return cached;

            try {
                var webhooks = channel.retrieveWebhooks().complete();
                String webhookUrl = webhooks.stream()
                        .filter(w -> "ZChat".equals(w.getName()))
                        .findFirst()
                        .map(w -> w.getUrl())
                        .orElseGet(() -> channel.createWebhook("ZChat").complete().getUrl());
                webhookClients.put(channel.getId(), WebhookClient.withUrl(webhookUrl));
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to create webhook for #"
                        + channel.getName() + ": " + e.getMessage());
            }
            return webhookClients.get(channel.getId());
        }
    }

    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) return "Player";

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
        if (!running || channel == null || message == null || message.isBlank()) return;
        if (pendingSends.incrementAndGet() > MAX_PENDING_SENDS) {
            pendingSends.decrementAndGet();
            plugin.getLogger().warning("Webhook queue is full; dropping a chat relay message.");
            return;
        }
        String safeName = sanitizeUsername(username);
        long delayMs = acquireSlot();
        WebhookMessageBuilder builder = new WebhookMessageBuilder()
                .setUsername(safeName)
                .setAvatarUrl(avatarUrl)
                .setContent(message);

        Runnable task = () -> {
            try {
                if (!running) return;
                WebhookClient client = getOrCreateWebhook(channel);
                if (client != null) {
                    doSend(client, channel, builder);
                }
            } finally {
                pendingSends.decrementAndGet();
            }
        };
        try {
            if (delayMs == 0) {
                plugin.getPlatformAdapter().runAsync(task);
            } else {
                scheduler.schedule(() -> plugin.getPlatformAdapter().runAsync(task),
                        delayMs, TimeUnit.MILLISECONDS);
            }
        } catch (RuntimeException e) {
            pendingSends.decrementAndGet();
            if (running) plugin.debug("Could not queue webhook message: " + e.getMessage());
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
                         + " - will be re-created on next message.");
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

    public int getWebhookCount() {
        return webhookClients.size();
    }

    public void shutdown() {
        running = false;
        scheduler.shutdownNow();
        for (WebhookClient client : webhookClients.values()) {
            try {
                client.close();
            } catch (Exception ignored) {
            }
        }
        webhookClients.clear();
    }
}

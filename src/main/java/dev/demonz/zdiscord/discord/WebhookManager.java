package dev.demonz.zdiscord.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Manages Discord webhooks for sending messages with player avatars.
 * Includes rate limiting to prevent Discord 429 (Too Many Requests) errors.
 */
public class WebhookManager {

    private final ZDiscord plugin;
    private final Map<String, WebhookClient> webhookClients = new ConcurrentHashMap<>();

    // Discord forbids "discord" and "clyde" in webhook usernames
    private static final Pattern INVALID_NAME = Pattern.compile("(?i)(discord|clyde)");

    // Rate limiter: max 4 sends per 2 seconds (Discord limit is 5/2s, leave
    // headroom)
    private static final int MAX_SENDS_PER_WINDOW = 4;
    private static final long WINDOW_MS = 2000;
    private final AtomicInteger sendCount = new AtomicInteger(0);
    private volatile long windowStart = System.currentTimeMillis();

    public WebhookManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    /**
     * Get or create a webhook for a text channel.
     */
    public WebhookClient getOrCreateWebhook(TextChannel channel) {
        return webhookClients.computeIfAbsent(channel.getId(), id -> {
            try {
                var webhooks = channel.retrieveWebhooks().complete();
                var existing = webhooks.stream()
                        .filter(w -> w.getName().equals("ZChat"))
                        .findFirst();

                String webhookUrl;
                if (existing.isPresent()) {
                    webhookUrl = existing.get().getUrl();
                } else {
                    var webhook = channel.createWebhook("ZChat").complete();
                    webhookUrl = webhook.getUrl();
                }
                return WebhookClient.withUrl(webhookUrl);
            } catch (Exception e) {
                plugin.getLogger()
                        .warning("Failed to create webhook for #" + channel.getName() + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Sanitize a username to comply with Discord's webhook username rules.
     * Discord rejects usernames containing "discord" or "clyde" (case-insensitive).
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty())
            return "Player";
        // Replace forbidden words
        String sanitized = INVALID_NAME.matcher(username).replaceAll("*$1*");
        // Username must be 1-80 chars
        if (sanitized.length() > 80)
            sanitized = sanitized.substring(0, 80);
        if (sanitized.isEmpty())
            sanitized = "Player";
        return sanitized;
    }

    /**
     * Check and update the rate limiter.
     * 
     * @return true if the request can proceed, false if rate limited
     */
    private boolean acquireRateLimit() {
        long now = System.currentTimeMillis();
        if (now - windowStart >= WINDOW_MS) {
            // Reset window
            windowStart = now;
            sendCount.set(1);
            return true;
        }
        return sendCount.incrementAndGet() <= MAX_SENDS_PER_WINDOW;
    }

    /**
     * Send a message via webhook with a custom username and avatar.
     * Rate-limited to prevent Discord 429 errors.
     */
    public void sendWebhookMessage(TextChannel channel, String username, String avatarUrl, String message) {
        WebhookClient client = getOrCreateWebhook(channel);
        if (client == null)
            return;

        String safeName = sanitizeUsername(username);

        plugin.getPlatformAdapter().runAsync(() -> {
            if (!acquireRateLimit()) {
                // Delay the message slightly to avoid hitting Discord rate limits
                try {
                    Thread.sleep(WINDOW_MS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            try {
                WebhookMessageBuilder builder = new WebhookMessageBuilder()
                        .setUsername(safeName)
                        .setAvatarUrl(avatarUrl)
                        .setContent(message);
                client.send(builder.build()).get(); // block to catch errors
            } catch (Exception e) {
                String errorMsg = e.getMessage() != null ? e.getMessage() : "";
                // If webhook was deleted (404), invalidate cache so it gets re-created
                if (errorMsg.contains("404") || errorMsg.contains("Unknown Webhook")) {
                    webhookClients.remove(channel.getId());
                    plugin.debug(
                            "Webhook invalidated for #" + channel.getName() + " — will re-create on next message.");
                } else {
                    plugin.debug("Webhook send failed: " + errorMsg);
                }
            }
        });
    }

    /**
     * Shutdown all webhook clients.
     */
    public void shutdown() {
        webhookClients.values().forEach(client -> {
            if (client != null) {
                client.close();
            }
        });
        webhookClients.clear();
    }
}

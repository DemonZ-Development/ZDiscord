/*
 * Copyright 2024 DemonZ Development
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

/**
 * Manages Discord webhooks for sending chat messages with player avatars.
 *
 * <p>Implements a token-bucket-style rate limiter that schedules delayed
 * sends via a small scheduled executor instead of blocking the calling
 * thread with {@code Thread.sleep}.</p>
 */
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

    /**
     * Sanitize a username for Discord's webhook username rules.
     * Discord rejects usernames containing "discord" or "clyde" (case-insensitive),
     * and any username that exceeds 80 characters.
     */
    private String sanitizeUsername(String username) {
        if (username == null || username.isEmpty()) {
            return "Player";
        }
        // Remove forbidden words rather than wrapping them in asterisks — the
        // previous approach replaced "discord" with "*discord*" but asterisks
        // are also rejected by Discord.
        String sanitized = FORBIDDEN_NAME.matcher(username).replaceAll("Player");
        if (sanitized.length() > 80) {
            sanitized = sanitized.substring(0, 80);
        }
        if (sanitized.isEmpty()) {
            sanitized = "Player";
        }
        return sanitized;
    }

    /**
     * Send a message via webhook. If the rate limit is currently saturated,
     * the send is scheduled for the next available slot instead of blocking
     * the calling thread.
     */
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
                        + " — will be re-created on next message.");
            } else {
                plugin.debug("Webhook send failed: " + errorMsg);
            }
        }
    }

    /**
     * Reserve a rate-limit slot. Returns 0 if the send can proceed
     * immediately, or the number of milliseconds to wait before sending.
     *
     * <p>The slot resets forward in time, so a long idle period will
     * not cause the next send to be delayed by the accumulated offset.</p>
     */
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

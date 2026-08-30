package dev.demonz.zdiscord.modules;

import club.minnced.discord.webhook.WebhookClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebhookCompatibilityTest {

    @Test
    void legacyWebhookClientRunsOnJdaOkHttpRuntime() {
        WebhookClient client = WebhookClient.withUrl(
                "https://discord.com/api/webhooks/123456789012345678/"
                        + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_ab");
        try {
            assertEquals(123456789012345678L, client.getId());
        } finally {
            client.close();
        }
        assertTrue(client.isShutdown());
    }
}

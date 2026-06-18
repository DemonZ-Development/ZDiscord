package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.testsupport.BukkitStub;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatusEmbedBuilderTest {

    @BeforeEach
    void setUp() {
        BukkitStub.install();
    }

    @AfterEach
    void tearDown() {
        BukkitStub.uninstall();
    }

    @Test
    void buildsEmbedWithExpectedFields() {
        Supplier<net.dv8tion.jda.api.entities.Guild> emptyGuild = () -> null;

        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                emptyGuild,
                "Server Status",
                "#5865F2",
                "play.example.com",
                30,
                true, true, true,
                18.0, 15.0);

        MessageEmbed embed = StatusEmbedBuilder.build(ctx);
        assertNotNull(embed);

        boolean hasStatus = embed.getFields().stream()
                .anyMatch(f -> "Status".equals(f.getName()));
        boolean hasPlayers = embed.getFields().stream()
                .anyMatch(f -> "Players".equals(f.getName()));
        boolean hasTps = embed.getFields().stream()
                .anyMatch(f -> f.getName() != null && f.getName().startsWith("TPS"));
        boolean hasMemory = embed.getFields().stream()
                .anyMatch(f -> "Memory".equals(f.getName()));

        assertTrue(hasStatus, "Status field should be present");
        assertTrue(hasPlayers, "Players field should be present");
        assertTrue(hasTps, "TPS field should be present");
        assertTrue(hasMemory, "Memory field should be present");
    }

    @Test
    void titleIsSetFromContext() {
        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                () -> null, "My Cool Server", "#2ECC71", "test.local", 30,
                true, true, true, 18.0, 15.0);

        MessageEmbed embed = StatusEmbedBuilder.build(ctx);
        assertNotNull(embed);
        assertNotNull(embed.getTitle());
        assertTrue(embed.getTitle().contains("Online"));
    }

    @Test
    void playerBarReflectsOnlineCount() {
        StatusEmbedBuilder.StatusContext ctx = new StatusEmbedBuilder.StatusContext();
        ctx.online = true;
        ctx.onlineCount = 5;
        ctx.maxCount = 10;
        ctx.tps = 20.0;
        ctx.tpsWarning = 18.0;
        ctx.tpsCritical = 15.0;
        ctx.showPlayers = true;
        ctx.showTps = true;
        ctx.showMemory = true;
        ctx.serverIp = "x";
        ctx.usedMemoryMb = 100;
        ctx.maxMemoryMb = 1000;
        ctx.updateIntervalSeconds = 30;

        MessageEmbed embed = StatusEmbedBuilder.build(ctx);
        assertNotNull(embed);
        boolean found = embed.getFields().stream()
                .anyMatch(f -> "Players".equals(f.getName())
                        && f.getValue() != null
                        && f.getValue().contains("5"));
        assertTrue(found, "Player count '5' should appear in the Players field");
    }

    @Test
    void hiddenTpsSectionWhenDisabled() {
        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                () -> null, "Status", "#5865F2", "ip", 30,
                true, false, true, 18.0, 15.0);
        MessageEmbed embed = StatusEmbedBuilder.build(ctx);
        assertNotNull(embed);
        boolean hasTps = embed.getFields().stream()
                .anyMatch(f -> f.getName() != null && f.getName().startsWith("TPS"));
        assertFalse(hasTps, "TPS field should be hidden when showTps=false");
    }
}

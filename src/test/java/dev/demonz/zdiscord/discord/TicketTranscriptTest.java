package dev.demonz.zdiscord.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TicketTranscriptTest {
    @Test void exportsMoreThanOneHistoryPageIncludingNewestInChronologicalOrder() throws Exception {
        List<java.nio.file.Path> parts;
        try (var transcript = new TicketTranscript("ticket-test")) {
            for (int i = 250; i >= 1; i--) transcript.append("Message " + String.format("%03d", i) + " 🎃\n\n");
            parts = transcript.finish(512);
            assertTrue(parts.size() > 1);
            StringBuilder all = new StringBuilder();
            for (var part : parts) {
                assertTrue(Files.size(part) <= 512);
                all.append(Files.readString(part));
            }
            String text = all.toString();
            assertEquals(250, transcript.size());
            int previous = -1;
            for (int i = 1; i <= 250; i++) {
                String marker = "Message " + String.format("%03d", i) + " 🎃";
                int at = text.indexOf(marker);
                assertTrue(at > previous, marker);
                assertEquals(at, text.lastIndexOf(marker), "Each message must appear once");
                previous = at;
            }
            assertTrue(text.contains("Message 250 🎃"));
        }
        for (var part : parts) assertFalse(Files.exists(part));
    }

    @Test void emptyHistoryHasNoUploadsAndErrorsStillCleanTemporaryFiles() throws Exception {
        try (var transcript = new TicketTranscript("empty")) {
            assertTrue(transcript.finish(512).isEmpty());
            transcript.append("x".repeat(1_000));
            assertThrows(java.io.IOException.class, () -> transcript.finish(512));
        }
    }

    @Test void embedOnlyMessagesAndEditedContentAreNotLost() {
        var user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[]{User.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getEffectiveName" -> "Staff";
                    case "getId" -> "123";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        var message = (Message) Proxy.newProxyInstance(Message.class.getClassLoader(), new Class<?>[]{Message.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAuthor" -> user;
                    case "getId" -> "456";
                    case "getTimeCreated" -> OffsetDateTime.parse("2026-10-08T12:00:00Z");
                    case "getContentDisplay" -> "";
                    case "isEdited" -> true;
                    case "getMessageReference" -> null;
                    case "getAttachments", "getStickers" -> List.of();
                    case "getEmbeds" -> List.of(new EmbedBuilder().setTitle("Subject").setDescription("Details")
                            .addField("Status", "Open", false).setImage("https://example.com/image.png").build());
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        String text = TicketTranscript.format(message);
        assertTrue(text.contains("[Edited]"));
        assertTrue(text.contains("message `456`"));
        assertTrue(text.contains("Embed: Subject\nDetails\nStatus: Open"));
        assertTrue(text.contains("https://example.com/image.png"));
    }
}

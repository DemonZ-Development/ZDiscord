package dev.demonz.zdiscord.util;

import net.md_5.bungee.api.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiscordAttachmentRendererTest {
    @Test void everyAttachmentGetsItsOwnClickableUrl() {
        var rendered = DiscordAttachmentRenderer.render("Hello", "&e[%filename%]", List.of(
                new DiscordAttachmentRenderer.Attachment("first.png", "https://cdn.discordapp.com/first.png"),
                new DiscordAttachmentRenderer.Attachment("second.txt", "https://cdn.discordapp.com/second.txt")));
        var children = rendered[0].getExtra();
        var links = children.stream().filter(c -> c.getClickEvent() != null).toList();
        assertEquals(2, links.size());
        assertEquals(ClickEvent.Action.OPEN_URL, links.get(0).getClickEvent().getAction());
        assertEquals("https://cdn.discordapp.com/first.png", links.get(0).getClickEvent().getValue());
        assertEquals("[second.txt]", links.get(1).toPlainText());
        assertEquals("https://cdn.discordapp.com/second.txt", links.get(1).getClickEvent().getValue());
    }

    @Test void nonWebUrlsAreNotClickableAndEmptyAttachmentsPreserveChat() {
        var rendered = DiscordAttachmentRenderer.render("Hello", "[Attachment]", List.of(
                new DiscordAttachmentRenderer.Attachment("bad", "file:///etc/passwd"),
                new DiscordAttachmentRenderer.Attachment("bad", "javascript:alert(1)")));
        assertEquals("Hello", rendered[0].toPlainText());
        assertTrue(rendered[0].getExtra().stream().allMatch(c -> c.getClickEvent() == null));
    }
}

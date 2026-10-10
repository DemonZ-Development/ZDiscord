package dev.demonz.zdiscord.util;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;

import java.net.URI;
import java.util.List;

@SuppressWarnings("deprecation")
public final class DiscordAttachmentRenderer {
    private DiscordAttachmentRenderer() { }

    public record Attachment(String filename, String url) { }

    public static BaseComponent[] render(String message, String label, List<Attachment> attachments) {
        TextComponent root = new TextComponent(TextComponent.fromLegacyText(message));
        for (Attachment attachment : attachments) {
            if (!isWebUrl(attachment.url())) continue;
            root.addExtra(" ");
            String text = ColorUtil.colorize(label.replace("%filename%", attachment.filename()));
            TextComponent link = new TextComponent(TextComponent.fromLegacyText(text));
            link.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, attachment.url()));
            root.addExtra(link);
        }
        return new BaseComponent[]{root};
    }

    private static boolean isWebUrl(String value) {
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException | NullPointerException ignored) { return false; }
    }
}

package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ColorUtilTest {

    @Test
    void parsesHashHex() {
        assertEquals(0x2ECC71, ColorUtil.parseHex("#2ECC71").getRGB() & 0xFFFFFF);
    }

    @Test
    void parses0xHex() {
        assertEquals(0x5865F2, ColorUtil.parseHex("0x5865F2").getRGB() & 0xFFFFFF);
    }

    @Test
    void nullFallsBackToBlurple() {
        assertNotNull(ColorUtil.parseHex(null));
    }

    @Test
    void emptyFallsBackToBlurple() {
        assertEquals(0x5865F2,
                ColorUtil.parseHex("not a color").getRGB() & 0xFFFFFF);
    }

    @Test
    void roundtripsToHex() {
        assertEquals("#2ecc71", ColorUtil.toHex(ColorUtil.parseHex("#2ECC71")));
    }

    @Test
    void stripColorRemovesAmpersandCodes() {
        assertEquals("hello", ColorUtil.stripColor("&ahello"));
        assertEquals("hello world", ColorUtil.stripColor("&ahello &bworld"));
    }

    @Test
    void stripColorRemovesSectionCodes() {
        assertEquals("hello", ColorUtil.stripColor("\u00A7ahello"));
    }

    @Test
    void stripColorHandlesNull() {
        assertEquals("", ColorUtil.stripColor(null));
    }

    @Test
    void stripColorLeavesPlainTextAlone() {
        assertTrue(ColorUtil.stripColor("plain text").equals("plain text"));
    }

    @Test
    void toDiscordMarkdownConvertsBold() {
        assertEquals("**hi**", ColorUtil.toDiscordMarkdown("&lhi&r"));
    }

    @Test
    void toDiscordMarkdownConvertsItalic() {
        assertEquals("*hi*", ColorUtil.toDiscordMarkdown("&ohi&r"));
    }

    @Test
    void toDiscordMarkdownConvertsUnderline() {
        assertEquals("__hi__", ColorUtil.toDiscordMarkdown("&nhi&r"));
    }

    @Test
    void toDiscordMarkdownConvertsStrikethrough() {
        assertEquals("~~hi~~", ColorUtil.toDiscordMarkdown("&mhi&r"));
    }

    @Test
    void toDiscordMarkdownDropsColorCodes() {
        assertEquals("hello", ColorUtil.toDiscordMarkdown("&ahello&r"));
    }

    @Test
    void toDiscordMarkdownHandlesSectionSign() {
        assertEquals("**hi**", ColorUtil.toDiscordMarkdown("\u00A7lhi\u00A7r"));
    }

    @Test
    void toDiscordMarkdownClosesNestedRuns() {




        assertEquals("**a *b***", ColorUtil.toDiscordMarkdown("&la &ob&r"));
    }

    @Test
    void toDiscordMarkdownTogglesBold() {

        assertEquals("**hi**", ColorUtil.toDiscordMarkdown("&lhi&l"));
    }

    @Test
    void toDiscordMarkdownHandlesEmptyString() {
        assertEquals("", ColorUtil.toDiscordMarkdown(""));
    }

    @Test
    void toDiscordMarkdownHandlesNull() {
        assertEquals("", ColorUtil.toDiscordMarkdown(null));
    }
}

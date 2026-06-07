/*
 * Copyright 2026 DemonZ Development
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
        // Italic is opened inside bold and both are closed
        // by &r (reset). Discord renders this as bold text
        // containing a single italic span, terminated by a
        // bold-italic boundary marker.
        assertEquals("**a *b***", ColorUtil.toDiscordMarkdown("&la &ob&r"));
    }

    @Test
    void toDiscordMarkdownTogglesBold() {
        // The second &l toggles bold off.
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

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
}

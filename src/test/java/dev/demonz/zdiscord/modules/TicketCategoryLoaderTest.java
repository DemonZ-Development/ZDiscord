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

package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.modules.TicketModule.TicketCategory;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TicketCategoryLoaderTest {

    @Test
    void emptyConfigReturnsEmptyMap() {
        YamlConfiguration cfg = new YamlConfiguration();
        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        assertNotNull(cats);
        assertTrue(cats.isEmpty());
    }

    @Test
    void parsesAllConfiguredCategories() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tickets.categories.general.label", "General Support");
        cfg.set("tickets.categories.general.description", "Anything");
        cfg.set("tickets.categories.general.emoji", "\uD83D\uDCAC");
        cfg.set("tickets.categories.general.color", "#5865F2");

        cfg.set("tickets.categories.bug.label", "Bug Report");
        cfg.set("tickets.categories.bug.description", "Broken things");
        cfg.set("tickets.categories.bug.emoji", "\uD83D\uDC1B");
        cfg.set("tickets.categories.bug.color", "#E67E22");

        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        assertEquals(2, cats.size());

        TicketCategory general = cats.get("general");
        assertNotNull(general);
        assertEquals("general", general.id);
        assertEquals("General Support", general.label);
        assertEquals("Anything", general.description);
        assertEquals("\uD83D\uDCAC", general.emoji);
        assertEquals("#5865F2", general.colorHex);

        TicketCategory bug = cats.get("bug");
        assertNotNull(bug);
        assertEquals("Bug Report", bug.label);
        assertEquals("#E67E22", bug.colorHex);
    }

    @Test
    void missingOptionalFieldsGetDefaults() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tickets.categories.bare.id", "bare");
        cfg.set("tickets.categories.bare.label", "Bare");
        // no description, emoji, or color

        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        TicketCategory c = cats.get("bare");
        assertNotNull(c);
        assertEquals("", c.description);
        assertEquals("", c.emoji);
        assertEquals("#5865F2", c.colorHex);
    }

    @Test
    void preservesInsertionOrder() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tickets.categories.z.id", "z");
        cfg.set("tickets.categories.z.label", "Z");
        cfg.set("tickets.categories.a.id", "a");
        cfg.set("tickets.categories.a.label", "A");
        cfg.set("tickets.categories.m.id", "m");
        cfg.set("tickets.categories.m.label", "M");

        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        // Yaml's LinkedHashMap preserves order, so this should be z, a, m.
        String[] order = cats.keySet().toArray(new String[0]);
        assertEquals("z", order[0]);
        assertEquals("a", order[1]);
        assertEquals("m", order[2]);
    }

    @Test
    void skipsNonSectionEntries() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tickets.categories.simple-bug", "just-a-string");
        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        assertTrue(cats.isEmpty(),
                "Non-section values should be skipped, not parsed as categories");
    }

    @Test
    void nullRootReturnsEmpty() {
        Map<String, TicketCategory> cats = TicketModule.loadCategories(null);
        assertNotNull(cats);
        assertTrue(cats.isEmpty());
    }

    @Test
    void defaultCategoryIsFirst() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tickets.categories.first.label", "First");
        cfg.set("tickets.categories.second.label", "Second");
        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        assertFalse(cats.isEmpty());
        assertEquals("first", cats.keySet().iterator().next());
    }

    @Test
    void missingCategoryReturnsNull() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("tickets.categories.alpha.label", "Alpha");
        Map<String, TicketCategory> cats = TicketModule.loadCategories(cfg);
        assertNull(cats.get("nonexistent"));
    }
}

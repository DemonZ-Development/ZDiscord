package dev.demonz.zdiscord.modules;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ReactionRoleConfigTest {
    private static final String MESSAGE = "1557496612056735844";
    private static final String ROLE = "1450764101121999019";

    @Test void loadsUnicodeAndCustomEmojiWithBothPermissionKeys() {
        var config = new YamlConfiguration();
        config.set("reaction-roles.mappings", List.of(
                Map.of("message-id", MESSAGE, "emoji", "✅", "role-id", ROLE, "minecraft-permission", "group.member"),
                Map.of("message-id", MESSAGE, "emoji", "<:member:1450764101121999019>", "role-id", ROLE, "permission", "test.member")));
        var mappings = ReactionRoleModule.loadConfiguredMappings(config, fail -> fail(fail));
        assertEquals(2, mappings.get(MESSAGE).size());
        assertEquals("group.member", mappings.get(MESSAGE).get("✅").permission);
        assertEquals("test.member", mappings.get(MESSAGE).get("member:" + ROLE).permission);
    }

    @Test void rejectsIncompleteIdsAndInjectedPermissions() {
        var config = new YamlConfiguration();
        config.set("reaction-roles.mappings", List.of(
                Map.of("message-id", MESSAGE, "emoji", "✅"),
                Map.of("message-id", "wrong", "emoji", "✅", "role-id", ROLE),
                Map.of("message-id", MESSAGE, "emoji", "✅", "role-id", ROLE, "permission", "member true; op someone")));
        var warnings = new ArrayList<String>();
        assertTrue(ReactionRoleModule.loadConfiguredMappings(config, warnings::add).isEmpty());
        assertEquals(3, warnings.size());
    }

    @Test void removingConfiguredEntriesRemovesThemOnReload() {
        var config = new YamlConfiguration();
        config.set("reaction-roles.mappings", List.of(Map.of("message-id", MESSAGE, "emoji", "✅", "role-id", ROLE)));
        assertEquals(1, ReactionRoleModule.loadConfiguredMappings(config, ignored -> { }).size());
        config.set("reaction-roles.mappings", List.of());
        assertTrue(ReactionRoleModule.loadConfiguredMappings(config, ignored -> { }).isEmpty());
    }
}

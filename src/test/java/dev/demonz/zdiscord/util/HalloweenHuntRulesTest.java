package dev.demonz.zdiscord.util;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HalloweenHuntRulesTest {

    @Test
    void defaultsCountHostileMobsAndExcludePassiveMobsAndPlayers() {
        HalloweenHuntRules rules = new HalloweenHuntRules(new YamlConfiguration());

        assertEquals(1, points(rules, EntityType.ZOMBIE, true));
        assertEquals(0, points(rules, EntityType.COW, false));
        assertEquals(0, points(rules, EntityType.PLAYER, false));
        assertEquals(0, points(rules, EntityType.PLAYER, true));
    }

    @Test
    void bossesCountEvenWithoutTheHostileMarker() {
        HalloweenHuntRules rules = new HalloweenHuntRules(new YamlConfiguration());

        assertEquals(5, points(rules, EntityType.WITHER, false));
        assertEquals(5, points(rules, EntityType.ENDER_DRAGON, false));
        assertEquals(5, points(rules, EntityType.ELDER_GUARDIAN, false));
    }

    @Test
    void configuredBossWeightIsClampedToAtLeastOne() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.boss-multiplier", 12);
        assertEquals(12, points(new HalloweenHuntRules(config), EntityType.WITHER, false));

        config.set("halloween.boss-multiplier", 0);
        assertEquals(1, points(new HalloweenHuntRules(config), EntityType.WITHER, false));

        config.set("halloween.boss-multiplier", -5);
        assertEquals(1, points(new HalloweenHuntRules(config), EntityType.WITHER, false));
    }

    @Test
    void creativeAndSpectatorCannotScoreByDefault() {
        HalloweenHuntRules rules = new HalloweenHuntRules(new YamlConfiguration());

        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.CREATIVE, "world", false));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SPECTATOR, "world", false));
        assertEquals(1, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.ADVENTURE, "world", false));
    }

    @Test
    void worldAllowlistUsesExactConfiguredNames() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.hunting.worlds", List.of("world_nether"));
        HalloweenHuntRules rules = new HalloweenHuntRules(config);

        assertEquals(1, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, "world_nether", false));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, "World_nether", false));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, "world", false));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, null, false));

        config.set("halloween.hunting.worlds", List.of());
        assertEquals(1, points(new HalloweenHuntRules(config), EntityType.ZOMBIE, true));
    }

    @Test
    void mobAllowlistRestrictsHostileMobsAndBossesWithoutEnablingPassiveMobsOrPvp() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.hunting.mob-types", List.of("zombie", "WITHER", "COW", "PLAYER"));
        HalloweenHuntRules rules = new HalloweenHuntRules(config);

        assertEquals(1, points(rules, EntityType.ZOMBIE, true));
        assertEquals(5, points(rules, EntityType.WITHER, false));
        assertEquals(0, points(rules, EntityType.SKELETON, true));
        assertEquals(0, points(rules, EntityType.ENDER_DRAGON, false));
        assertEquals(0, points(rules, EntityType.COW, false));
        assertEquals(0, points(rules, EntityType.PLAYER, true));
    }

    @Test
    void spawnerMobsRequireAnExplicitOptIn() {
        YamlConfiguration config = new YamlConfiguration();
        assertEquals(0, new HalloweenHuntRules(config)
                .pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, "world", true));

        config.set("halloween.hunting.count-spawner-mobs", true);
        assertEquals(1, new HalloweenHuntRules(config)
                .pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, "world", true));
    }

    @Test
    void invalidMobNamesNeverBroadenAnExplicitAllowlist() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.hunting.mob-types", List.of("ZOMBIEE"));
        HalloweenHuntRules invalidOnly = new HalloweenHuntRules(config);
        assertEquals(0, points(invalidOnly, EntityType.ZOMBIE, true));
        assertEquals(0, points(invalidOnly, EntityType.WITHER, false));

        config.set("halloween.hunting.mob-types", List.of("ZOMBIE", "NOT_A_MOB"));
        HalloweenHuntRules mixed = new HalloweenHuntRules(config);
        assertEquals(1, points(mixed, EntityType.ZOMBIE, true));
        assertEquals(0, points(mixed, EntityType.SKELETON, true));
    }

    @Test
    void invalidGameModesKeepDefaultExclusionsAndValidConfiguredExclusions() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.hunting.excluded-game-modes", List.of("survival", "CREATIV"));
        HalloweenHuntRules rules = new HalloweenHuntRules(config);

        assertEquals(0, points(rules, EntityType.ZOMBIE, true));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.CREATIVE, "world", false));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SPECTATOR, "world", false));
        assertEquals(1, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.ADVENTURE, "world", false));
    }

    @Test
    void emptyGameModeExclusionsExplicitlyPermitAllModes() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.hunting.excluded-game-modes", List.of());
        HalloweenHuntRules rules = new HalloweenHuntRules(config);

        assertEquals(1, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.CREATIVE, "world", false));
        assertEquals(1, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SPECTATOR, "world", false));
    }

    @Test
    void malformedNonemptyMobListsNeverClearTheRestriction() throws Exception {
        for (String value : List.of("[null]", "[{ZOMBIE: true}]", "[42]", "{ZOMBIE: true}")) {
            HalloweenHuntRules rules = new HalloweenHuntRules(huntingConfig("mob-types", value));
            assertEquals(0, points(rules, EntityType.ZOMBIE, true), value);
            assertEquals(0, points(rules, EntityType.WITHER, false), value);
        }

        HalloweenHuntRules mixed = new HalloweenHuntRules(
                huntingConfig("mob-types", "[null, {SKELETON: true}, ZOMBIE]"));
        assertEquals(1, points(mixed, EntityType.ZOMBIE, true));
        assertEquals(0, points(mixed, EntityType.SKELETON, true));
        assertEquals(1, points(new HalloweenHuntRules(huntingConfig("mob-types", "[]")),
                EntityType.ZOMBIE, true));
    }

    @Test
    void malformedNonemptyWorldListsNeverPermitEveryWorld() throws Exception {
        for (String value : List.of("[null]", "[{world: true}]", "[42]", "{world: true}")) {
            HalloweenHuntRules rules = new HalloweenHuntRules(huntingConfig("worlds", value));
            assertEquals(0, points(rules, EntityType.ZOMBIE, true), value);
            assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true,
                    GameMode.SURVIVAL, "42", false), value);
        }

        HalloweenHuntRules mixed = new HalloweenHuntRules(
                huntingConfig("worlds", "[null, {world_nether: true}, world]"));
        assertEquals(1, points(mixed, EntityType.ZOMBIE, true));
        assertEquals(0, mixed.pointsFor(EntityType.ZOMBIE, true,
                GameMode.SURVIVAL, "world_nether", false));
        assertEquals(1, points(new HalloweenHuntRules(huntingConfig("worlds", "[]")),
                EntityType.ZOMBIE, true));
    }

    @Test
    void malformedGameModeEntriesKeepDefaultsAndValidExclusions() throws Exception {
        for (String value : List.of("[null]", "[{CREATIVE: true}]", "[42]",
                "{CREATIVE: true}", "null", "[SURVIVAL, null, {ADVENTURE: true}]")) {
            HalloweenHuntRules rules = new HalloweenHuntRules(
                    huntingConfig("excluded-game-modes", value));
            assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true,
                    GameMode.CREATIVE, "world", false), value);
            assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true,
                    GameMode.SPECTATOR, "world", false), value);
            assertEquals(1, rules.pointsFor(EntityType.ZOMBIE, true,
                    GameMode.ADVENTURE, "world", false), value);
            assertEquals(value.contains("SURVIVAL") ? 0 : 1,
                    points(rules, EntityType.ZOMBIE, true), value);
        }
    }

    @Test
    void rulesRemainAnImmutableSnapshotUntilReloaded() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("halloween.hunting.worlds", List.of("world"));
        config.set("halloween.hunting.mob-types", List.of("ZOMBIE"));
        config.set("halloween.boss-multiplier", 9);
        HalloweenHuntRules rules = new HalloweenHuntRules(config);

        config.set("halloween.hunting.worlds", List.of("other"));
        config.set("halloween.hunting.mob-types", List.of("SKELETON"));
        config.set("halloween.hunting.excluded-game-modes", List.of("SURVIVAL"));

        assertEquals(1, points(rules, EntityType.ZOMBIE, true));
        assertEquals(0, points(rules, EntityType.SKELETON, true));
        assertEquals(0, rules.pointsFor(EntityType.ZOMBIE, true, GameMode.SURVIVAL, "other", false));
    }

    private static long points(HalloweenHuntRules rules, EntityType type, boolean hostile) {
        return rules.pointsFor(type, hostile, GameMode.SURVIVAL, "world", false);
    }

    private static YamlConfiguration huntingConfig(String key, String value) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("halloween:\n  hunting:\n    " + key + ": " + value + "\n");
        return config;
    }
}

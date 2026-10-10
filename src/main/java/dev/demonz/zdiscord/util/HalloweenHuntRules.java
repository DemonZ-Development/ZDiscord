package dev.demonz.zdiscord.util;

import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class HalloweenHuntRules {

    private static final String HUNTING = "halloween.hunting.";
    private static final Set<EntityType> BOSSES = Set.of(
            EntityType.WITHER, EntityType.ENDER_DRAGON, EntityType.ELDER_GUARDIAN);
    private static final Set<GameMode> DEFAULT_EXCLUDED_MODES = Set.of(
            GameMode.CREATIVE, GameMode.SPECTATOR);

    private final long bossMultiplier;
    private final Set<String> worlds;
    private final boolean restrictWorlds;
    private final Set<GameMode> excludedGameModes;
    private final Set<EntityType> mobTypes;
    private final boolean restrictMobTypes;
    private final boolean countSpawnerMobs;

    public HalloweenHuntRules(ConfigurationSection config) {
        Objects.requireNonNull(config, "config");
        bossMultiplier = Math.max(1, config.getInt("halloween.boss-multiplier", 5));

        List<String> configuredWorlds = stringEntries(config, "worlds");
        worlds = Set.copyOf(configuredWorlds);
        restrictWorlds = isRestriction(config, "worlds");

        EnumSet<GameMode> modes = EnumSet.noneOf(GameMode.class);
        String modesPath = HUNTING + "excluded-game-modes";
        Object configuredModes = config.get(modesPath);
        boolean invalidModes = !(configuredModes instanceof List<?>);
        if (configuredModes instanceof List<?> values) {
            for (Object entry : values) {
                if (!(entry instanceof String value)) {
                    invalidModes = true;
                    continue;
                }
                try {
                    modes.add(GameMode.valueOf(value.trim().toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                    invalidModes = true;
                }
            }
        }

        if (invalidModes) modes.addAll(DEFAULT_EXCLUDED_MODES);
        excludedGameModes = Set.copyOf(modes);

        List<String> configuredMobs = stringEntries(config, "mob-types");
        restrictMobTypes = isRestriction(config, "mob-types");
        EnumSet<EntityType> types = EnumSet.noneOf(EntityType.class);
        for (String value : configuredMobs) {
            try {
                types.add(EntityType.valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {

            }
        }
        mobTypes = Set.copyOf(types);
        countSpawnerMobs = config.getBoolean(HUNTING + "count-spawner-mobs", false);
    }

    private static List<String> stringEntries(ConfigurationSection config, String key) {
        return config.getList(HUNTING + key, List.of()).stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .toList();
    }

    private static boolean isRestriction(ConfigurationSection config, String key) {
        String path = HUNTING + key;
        if (!config.contains(path)) return false;
        Object configured = config.get(path);

        return !(configured instanceof List<?> values) || !values.isEmpty();
    }

    public long pointsFor(EntityType type, boolean hostile, GameMode gameMode,
                          String world, boolean fromSpawner) {
        if (type == null || type == EntityType.PLAYER || gameMode == null) return 0;
        boolean boss = BOSSES.contains(type);
        if (!hostile && !boss) return 0;
        if (excludedGameModes.contains(gameMode)) return 0;
        if (restrictWorlds && (world == null || !worlds.contains(world))) return 0;
        if (restrictMobTypes && !mobTypes.contains(type)) return 0;
        if (fromSpawner && !countSpawnerMobs) return 0;
        return boss ? bossMultiplier : 1;
    }
}

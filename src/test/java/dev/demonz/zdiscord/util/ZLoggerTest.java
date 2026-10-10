package dev.demonz.zdiscord.util;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZLoggerTest {
    @AfterEach void reset() { ZLogger.init(Logger.getLogger("ZLoggerTest"), null); }

    @Test void recognizesLegacyDebugWithoutKeepingAnUnusedDefaultKey() {
        var config = new YamlConfiguration();
        config.set("misc.debug", true);
        ZLogger.init(Logger.getLogger("ZLoggerTest"), config);
        assertTrue(ZLogger.isDebugMode());
    }

    @Test void disablingSuppressionRestoresLibraryLogging() {
        var config = new YamlConfiguration();
        ZLogger.init(Logger.getLogger("ZLoggerTest"), config);
        assertEquals(java.util.logging.Level.WARNING, Logger.getLogger("net.dv8tion.jda").getLevel());
        config.set("logging.suppress-jda", false);
        config.set("logging.suppress-hikari", false);
        ZLogger.init(Logger.getLogger("ZLoggerTest"), config);
        assertEquals(java.util.logging.Level.INFO, Logger.getLogger("net.dv8tion.jda").getLevel());
        assertEquals(java.util.logging.Level.INFO, Logger.getLogger("com.zaxxer.hikari").getLevel());
    }

    @Test void loggerLevelAndCategoryNamesDoNotDependOnServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var config = new YamlConfiguration();
            config.set("logging.level", "silent");
            config.set("logging.categories.api", "info");
            ZLogger.init(Logger.getLogger("ZLoggerTest"), config);
            assertEquals(ZLogger.Level.SILENT, ZLogger.getGlobalLevel());
            assertTrue(ZLogger.isEnabled(ZLogger.Category.API, ZLogger.Level.INFO));
            assertFalse(ZLogger.isEnabled(ZLogger.Category.SYSTEM, ZLogger.Level.INFO));
        } finally { Locale.setDefault(previous); }
    }
}

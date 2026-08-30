package dev.demonz.zdiscord.util;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class ZLogger {

    public enum Category {
        BOT, STORAGE, MODULES, EVENTS, API, SETUP, COMMANDS, WEBHOOK, SYSTEM
    }

    public enum Level {
        SILENT(0), ERROR(1), WARN(2), INFO(3), DEBUG(4), TRACE(5);

        final int priority;

        Level(int priority) {
            this.priority = priority;
        }
    }

    private static Logger bukkit;
    private static Level globalLevel = Level.INFO;
    private static final Map<Category, Level> categoryLevels = new EnumMap<>(Category.class);
    private static boolean compact = true;

    private ZLogger() {
    }

    public static void init(Logger bukkitLogger, FileConfiguration config) {
        bukkit = bukkitLogger;

        // one switch for troubleshooting: everything loud, JDA included
        boolean debugMode = config != null && config.getBoolean("logging.debug", false);

        String g = config != null ? config.getString("logging.level", "INFO") : "INFO";
        globalLevel = debugMode ? Level.DEBUG : parseLevel(g);

        compact = config == null || config.getBoolean("logging.compact", true);

        categoryLevels.clear();
        if (config != null && !debugMode && config.isConfigurationSection("logging.categories")) {
            for (Category cat : Category.values()) {
                String val = config.getString("logging.categories." + cat.name().toLowerCase(), null);
                if (val != null) {
                    categoryLevels.put(cat, parseLevel(val));
                }
            }
        }

        // JDA and Hikari are noisy as hell, default them to WARNING. Debug
        // mode lets them talk at INFO - their DEBUG would be a firehose.
        if (config == null || (config.getBoolean("logging.suppress-jda", true) && !debugMode)) {
            setJavaLogLevel("net.dv8tion.jda", java.util.logging.Level.WARNING);
        } else if (debugMode) {
            setJavaLogLevel("net.dv8tion.jda", java.util.logging.Level.INFO);
        }
        if (config == null || (config.getBoolean("logging.suppress-hikari", true) && !debugMode)) {
            setJavaLogLevel("com.zaxxer.hikari", java.util.logging.Level.WARNING);
        } else if (debugMode) {
            setJavaLogLevel("com.zaxxer.hikari", java.util.logging.Level.INFO);
        }
    }

    public static void init(Logger bukkitLogger) {
        init(bukkitLogger, null);
    }

    public static void info(Category cat, String message) {
        if (isEnabled(cat, Level.INFO)) {
            logger().info(tag(cat) + message);
        }
    }

    public static void warn(Category cat, String message) {
        if (isEnabled(cat, Level.WARN)) {
            logger().warning(tag(cat) + message);
        }
    }

    public static void error(Category cat, String message) {
        if (isEnabled(cat, Level.ERROR)) {
            logger().severe(tag(cat) + message);
        }
    }

    public static void error(Category cat, String message, Throwable throwable) {
        if (!isEnabled(cat, Level.ERROR)) return;
        logger().severe(tag(cat) + message);
        if (throwable != null) {
            StringWriter sw = new StringWriter();
            throwable.printStackTrace(new PrintWriter(sw));
            for (String line : sw.toString().split("\n")) {
                logger().severe(tag(cat) + line);
            }
        }
    }

    public static void debug(Category cat, String message) {
        if (isEnabled(cat, Level.DEBUG)) {
            logger().info(tag(cat) + "[debug] " + message);
        }
    }

    public static void debug(Category cat, Supplier<String> messageSupplier) {
        if (isEnabled(cat, Level.DEBUG)) {
            logger().info(tag(cat) + "[debug] " + messageSupplier.get());
        }
    }

    public static void trace(Category cat, String message) {
        if (isEnabled(cat, Level.TRACE)) {
            logger().info(tag(cat) + "[trace] " + message);
        }
    }

    public static void info(String message) {
        info(Category.SYSTEM, message);
    }

    public static void warn(String message) {
        warn(Category.SYSTEM, message);
    }

    public static void error(String message) {
        error(Category.SYSTEM, message);
    }

    public static boolean isEnabled(Category cat, Level level) {
        if (bukkit == null) return true;
        Level effective = categoryLevels.getOrDefault(cat, globalLevel);
        return level.priority <= effective.priority;
    }

    public static boolean isDebug(Category cat) {
        return isEnabled(cat, Level.DEBUG);
    }

    public static Level getGlobalLevel() {
        return globalLevel;
    }

    public static boolean isDebugMode() {
        return globalLevel == Level.DEBUG;
    }

    public static void setLevel(Category cat, Level level) {
        categoryLevels.put(cat, level);
    }

    private static String tag(Category cat) {
        return compact ? "[" + cat.name() + "] " : "";
    }

    private static Logger logger() {
        return bukkit != null ? bukkit : Logger.getLogger("ZDiscord");
    }

    private static Level parseLevel(String value) {
        if (value == null) return Level.INFO;
        try {
            return Level.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    private static void setJavaLogLevel(String loggerName, java.util.logging.Level level) {
        try {
            java.util.logging.Logger.getLogger(loggerName).setLevel(level);
        } catch (Exception ignored) {
        }
    }
}

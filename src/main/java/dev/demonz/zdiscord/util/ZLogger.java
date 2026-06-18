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


        String g = config != null ? config.getString("logging.level", "INFO") : "INFO";
        globalLevel = parseLevel(g);


        compact = config == null || config.getBoolean("logging.compact", true);


        categoryLevels.clear();
        if (config != null && config.isConfigurationSection("logging.categories")) {
            for (Category cat : Category.values()) {
                String key = "logging.categories." + cat.name().toLowerCase();
                String val = config.getString(key, null);
                if (val != null) {
                    categoryLevels.put(cat, parseLevel(val));
                }
            }
        }


        if (config == null || config.getBoolean("logging.suppress-jda", true)) {
            setJavaLogLevel("net.dv8tion.jda", java.util.logging.Level.WARNING);
        }
        if (config == null || config.getBoolean("logging.suppress-hikari", true)) {
            setJavaLogLevel("com.zaxxer.hikari", java.util.logging.Level.WARNING);
        }
    }


    public static void init(Logger bukkitLogger) {
        init(bukkitLogger, null);
    }



    public static void info(Category cat, String message) {
        if (isEnabled(cat, Level.INFO)) {
            bukkit.info(tag(cat) + message);
        }
    }

    public static void warn(Category cat, String message) {
        if (isEnabled(cat, Level.WARN)) {
            bukkit.warning(tag(cat) + message);
        }
    }

    public static void error(Category cat, String message) {
        if (isEnabled(cat, Level.ERROR)) {
            bukkit.severe(tag(cat) + message);
        }
    }

    public static void error(Category cat, String message, Throwable throwable) {
        if (isEnabled(cat, Level.ERROR)) {
            bukkit.severe(tag(cat) + message);
            if (throwable != null) {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                for (String line : sw.toString().split("\n")) {
                    bukkit.severe(tag(cat) + line);
                }
            }
        }
    }

    public static void debug(Category cat, String message) {
        if (isEnabled(cat, Level.DEBUG)) {
            bukkit.info(tag(cat) + "[debug] " + message);
        }
    }


    public static void debug(Category cat, Supplier<String> messageSupplier) {
        if (isEnabled(cat, Level.DEBUG)) {
            bukkit.info(tag(cat) + "[debug] " + messageSupplier.get());
        }
    }

    public static void trace(Category cat, String message) {
        if (isEnabled(cat, Level.TRACE)) {
            bukkit.info(tag(cat) + "[trace] " + message);
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
        if (bukkit == null) {
            return true;
        }
        Level effective = categoryLevels.getOrDefault(cat, globalLevel);
        return level.priority <= effective.priority;
    }

    public static boolean isDebug(Category cat) {
        return isEnabled(cat, Level.DEBUG);
    }

    public static void setLevel(Category cat, Level level) {
        categoryLevels.put(cat, level);
    }



    private static String tag(Category cat) {
        if (!compact) {
            return "";
        }
        return "[" + cat.name() + "] ";
    }

    private static Level parseLevel(String value) {
        if (value == null) {
            return Level.INFO;
        }
        try {
            return Level.valueOf(value.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }

    private static void setJavaLogLevel(String loggerName, java.util.logging.Level level) {
        try {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger(loggerName);
            logger.setLevel(level);
        } catch (Exception ignored) {


        }
    }
}

package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;

import java.util.logging.Logger;

public final class StartupBanner {

    private static final int BOX_WIDTH = 46;

    private StartupBanner() {
    }


    public static void print(ZDiscord plugin, long startupMs) {
        Logger log = plugin.getLogger();
        String version = plugin.getDescription().getVersion();
        String platform = plugin.getPlatformAdapter().getPlatformName();

        String botName = "Not connected";
        String guildName = "—";
        if (plugin.getBotManager() != null && plugin.getBotManager().isConnected()) {
            botName = plugin.getBotManager().getJda().getSelfUser().getName();
            var guild = plugin.getBotManager().getGuild();
            if (guild != null) {
                guildName = guild.getName();
            }
        }

        int activeModules = countActiveModules(plugin);
        String storage = plugin.getStorageManager() != null
                ? plugin.getStorageManager().getTypeName()
                : "none";

        log.info("");
        log.info("\u2554" + "\u2550".repeat(BOX_WIDTH) + "\u2557");
        log.info(pad("  ZDiscord v" + version));
        log.info(pad("  Platform: " + platform));
        log.info(pad("  Bot: " + botName + " \u2022 Guild: " + truncate(guildName, 20)));
        log.info(pad("  Modules: " + activeModules + "/15 \u2022 Storage: " + storage));
        log.info(pad("  Startup: " + startupMs + "ms"));
        log.info("\u255A" + "\u2550".repeat(BOX_WIDTH) + "\u255D");
        log.info("");
    }

    private static String pad(String text) {
        if (text.length() >= BOX_WIDTH) {
            return "\u2551" + text.substring(0, BOX_WIDTH) + "\u2551";
        }
        return "\u2551" + text + " ".repeat(BOX_WIDTH - text.length()) + "\u2551";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "—";
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen - 1) + "\u2026";
    }

    private static int countActiveModules(ZDiscord plugin) {
        int count = 0;
        if (plugin.getStatusModule() != null) count++;
        if (plugin.getLeaderboardModule() != null) count++;
        if (plugin.getTicketModule() != null) count++;
        if (plugin.getLinkModule() != null) count++;
        if (plugin.getAntiRaidModule() != null) count++;
        if (plugin.getPerformanceModule() != null) count++;
        if (plugin.getReactionRoleModule() != null) count++;
        if (plugin.getEmbedBuilderModule() != null) count++;
        if (plugin.getCommandLoggerModule() != null) count++;
        if (plugin.getStaffChatModule() != null) count++;
        if (plugin.getVoiceStatusModule() != null) count++;
        if (plugin.getConsoleModule() != null) count++;
        if (plugin.getFollowModule() != null) count++;
        return count;
    }
}

package dev.demonz.zdiscord.util;

import dev.demonz.zdiscord.ZDiscord;

import java.util.Arrays;
import java.util.logging.Logger;

public final class StartupBanner {

    private static final int BOX_WIDTH = 46;

    private StartupBanner() {
    }

    @SuppressWarnings("deprecation")
    public static void print(ZDiscord plugin, long startupMs) {
        Logger log = plugin.getLogger();
        String version = plugin.getDescription().getVersion();
        String platform = plugin.getPlatformAdapter().getPlatformName();

        String botName = "Not connected";
        String guildName = "-";
        if (plugin.getBotManager() != null && plugin.getBotManager().isConnected()) {
            botName = plugin.getBotManager().getJda().getSelfUser().getName();
            var guild = plugin.getBotManager().getGuild();
            if (guild != null) guildName = guild.getName();
        }

        ModuleCount moduleCount = countModules(
                plugin.getStatusModule(),
                plugin.getLiveStatsModule(),
                plugin.getLeaderboardModule(),
                plugin.getTicketModule(),
                plugin.getLinkModule(),
                plugin.getAntiRaidModule(),
                plugin.getPerformanceModule(),
                plugin.getReactionRoleModule(),
                plugin.getEmbedBuilderModule(),
                plugin.getCommandLoggerModule(),
                plugin.getStaffChatModule(),
                plugin.getVoiceStatusModule(),
                plugin.getConsoleModule(),
                plugin.getFollowModule(),
                plugin.getConfessionModule(),
                plugin.getIntegrationModule(),
                plugin.getHalloweenModule()
        );
        String storage = plugin.getStorageManager() != null
                ? plugin.getStorageManager().getTypeName() : "none";

        String bar = "=".repeat(BOX_WIDTH + 4);
        log.info("");
        log.info(bar);
        log.info(pad("  ZDiscord v" + version));
        log.info(pad("  Platform: " + platform));
        log.info(pad("  Bot: " + botName + " | Guild: " + truncate(guildName, 20)));
        log.info(pad("  Modules: " + moduleCount.active() + "/" + moduleCount.total() + " | Storage: " + storage));
        log.info(pad("  Startup: " + startupMs + "ms"));
        log.info(bar);
        log.info("");
    }

    private static String pad(String text) {
        if (text.length() >= BOX_WIDTH) {
            return "| " + text.substring(0, BOX_WIDTH) + " |";
        }
        return "| " + text + " ".repeat(BOX_WIDTH - text.length()) + " |";
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "-";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen - 1) + "..";
    }

    static ModuleCount countModules(Object... modules) {
        return new ModuleCount(
                Arrays.stream(modules).filter(module -> module != null).count(),
                modules.length
        );
    }

    record ModuleCount(long active, int total) {
    }
}

package dev.demonz.zdiscord;

import dev.demonz.zdiscord.api.ZDiscordAPI;
import dev.demonz.zdiscord.api.ZDiscordAPIImpl;
import dev.demonz.zdiscord.api.ZDiscordProvider;
import dev.demonz.zdiscord.config.ConfigManager;
import dev.demonz.zdiscord.config.MessageManager;
import dev.demonz.zdiscord.discord.BotManager;
import dev.demonz.zdiscord.discord.SetupCommand;
import dev.demonz.zdiscord.discord.SlashCommandManager;
import dev.demonz.zdiscord.discord.WebhookManager;
import dev.demonz.zdiscord.minecraft.commands.ConfessCommand;
import dev.demonz.zdiscord.minecraft.commands.DiscordCommand;
import dev.demonz.zdiscord.minecraft.commands.StaffChatCommand;
import dev.demonz.zdiscord.minecraft.commands.ZDiscordCommand;
import dev.demonz.zdiscord.minecraft.listeners.AdvancementListener;
import dev.demonz.zdiscord.minecraft.listeners.ChatListener;
import dev.demonz.zdiscord.minecraft.listeners.DeathListener;
import dev.demonz.zdiscord.minecraft.listeners.JoinQuitListener;
import dev.demonz.zdiscord.minecraft.listeners.LinkEnforcementListener;
import dev.demonz.zdiscord.minecraft.listeners.PaperChatListener;
import dev.demonz.zdiscord.minecraft.listeners.PaperStaffChatListener;
import dev.demonz.zdiscord.modules.AntiRaidModule;
import dev.demonz.zdiscord.modules.CommandLoggerModule;
import dev.demonz.zdiscord.modules.ConfessionModule;
import dev.demonz.zdiscord.modules.ConsoleModule;
import dev.demonz.zdiscord.modules.EmbedBuilderModule;
import dev.demonz.zdiscord.modules.FollowModule;
import dev.demonz.zdiscord.modules.LeaderboardModule;
import dev.demonz.zdiscord.modules.LinkModule;
import dev.demonz.zdiscord.modules.IntegrationModule;
import dev.demonz.zdiscord.modules.LiveStatsModule;
import dev.demonz.zdiscord.modules.PerformanceModule;
import dev.demonz.zdiscord.modules.ReactionRoleModule;
import dev.demonz.zdiscord.modules.StaffChatModule;
import dev.demonz.zdiscord.modules.StatusModule;
import dev.demonz.zdiscord.modules.TicketModule;
import dev.demonz.zdiscord.modules.VoiceStatusModule;
import dev.demonz.zdiscord.platform.FoliaAdapter;
import dev.demonz.zdiscord.platform.PaperAdapter;
import dev.demonz.zdiscord.platform.PlatformAdapter;
import dev.demonz.zdiscord.platform.SpigotAdapter;
import dev.demonz.zdiscord.storage.MySQLStorage;
import dev.demonz.zdiscord.storage.StorageManager;
import dev.demonz.zdiscord.storage.YamlStorage;
import dev.demonz.zdiscord.util.StartupBanner;
import dev.demonz.zdiscord.util.SkinUtil;
import dev.demonz.zdiscord.util.UpdateChecker;
import dev.demonz.zdiscord.util.ZLogger;
import org.bstats.bukkit.Metrics;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class ZDiscord extends JavaPlugin {

    private static final String ASYNC_CHAT_EVENT = "io.papermc.paper.event.player.AsyncChatEvent";

    private static ZDiscord instance;

    private PlatformAdapter platformAdapter;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private BotManager botManager;
    private WebhookManager webhookManager;
    private SlashCommandManager slashCommandManager;
    private SetupCommand setupCommand;
    private StorageManager storageManager;
    private boolean paperModern;

    private StatusModule statusModule;
    private LiveStatsModule liveStatsModule;
    private LeaderboardModule leaderboardModule;
    private TicketModule ticketModule;
    private LinkModule linkModule;
    private AntiRaidModule antiRaidModule;
    private PerformanceModule performanceModule;
    private ReactionRoleModule reactionRoleModule;
    private EmbedBuilderModule embedBuilderModule;
    private CommandLoggerModule commandLoggerModule;
    private StaffChatModule staffChatModule;
    private VoiceStatusModule voiceStatusModule;
    private ConsoleModule consoleModule;
    private FollowModule followModule;
    private ConfessionModule confessionModule;
    private IntegrationModule integrationModule;

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        ZLogger.init(getLogger(), configManager.getConfig());

        detectPlatform();
        ZLogger.info(ZLogger.Category.SYSTEM,
                "Starting ZDiscord v" + getDescription().getVersion()
                + " on " + platformAdapter.getPlatformName());

        SkinUtil.init(this);
        initStorage();

        new Metrics(this, 29652);

        botManager = new BotManager(this);
        confessionModule = new ConfessionModule(this);
        slashCommandManager = new SlashCommandManager(this);
        setupCommand = new SetupCommand(this);
        if (!botManager.connect()) {
            ZLogger.error(ZLogger.Category.BOT,
                    "Failed to connect to Discord. Check bot.token and bot.guild-id in config.yml.");
            ZLogger.warn(ZLogger.Category.BOT,
                    "The plugin will continue to load, but Discord features are disabled.");
        } else {
            webhookManager = new WebhookManager(this);
            slashCommandManager.registerCommands();
            initModules();
            integrationModule = new IntegrationModule(this);
            integrationModule.init();
        }

        registerListeners();
        registerCommands();

        if (configManager.getBoolean("api.enabled", true)) {
            ZDiscordProvider.register(new ZDiscordAPIImpl(this));
            getServer().getServicesManager().register(
                    ZDiscordAPI.class, ZDiscordProvider.get(), this, ServicePriority.Normal);
        }

        if (configManager.getBoolean("misc.update-checker", true)) {
            new UpdateChecker(this);
        }

        long elapsed = System.currentTimeMillis() - start;
        StartupBanner.print(this, elapsed);
    }

    @Override
    public void onDisable() {
        ZLogger.info(ZLogger.Category.SYSTEM, "Shutting down ZDiscord...");

        ZDiscordProvider.unregister();
        getServer().getServicesManager().unregisterAll(this);

        if (integrationModule != null) integrationModule.shutdown();

        if (statusModule != null) statusModule.shutdown();
        if (liveStatsModule != null) liveStatsModule.shutdown();
        if (leaderboardModule != null) leaderboardModule.shutdown();
        if (ticketModule != null) ticketModule.shutdown();
        if (linkModule != null) linkModule.shutdown();
        if (antiRaidModule != null) antiRaidModule.shutdown();
        if (performanceModule != null) performanceModule.shutdown();
        if (reactionRoleModule != null) reactionRoleModule.shutdown();
        if (commandLoggerModule != null) commandLoggerModule.shutdown();
        if (staffChatModule != null) staffChatModule.shutdown();
        if (voiceStatusModule != null) voiceStatusModule.shutdown();
        if (consoleModule != null) consoleModule.shutdown();
        if (followModule != null) followModule.shutdown();

        if (storageManager != null) {
            storageManager.shutdown();
            ZLogger.info(ZLogger.Category.SYSTEM, "Storage flushed.");
        }
        if (webhookManager != null) webhookManager.shutdown();
        if (botManager != null) botManager.shutdown();

        ZLogger.info(ZLogger.Category.SYSTEM, "ZDiscord shut down.");
        instance = null;
    }

    private void detectPlatform() {
        if (hasClass("io.papermc.paper.threadedregions.RegionizedServer")) {
            platformAdapter = new FoliaAdapter(this);
        } else if (hasClass(ASYNC_CHAT_EVENT)) {
            platformAdapter = new PaperAdapter(this);
        } else {
            platformAdapter = new SpigotAdapter(this);
        }

        paperModern = hasClass(ASYNC_CHAT_EVENT);
        if (paperModern) {
            ZLogger.info(ZLogger.Category.SYSTEM,
                    platformAdapter instanceof FoliaAdapter
                            ? "Folia detected - modern Paper chat events will be used."
                            : "Paper detected - modern chat events will be used.");
        }
    }

    private boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void initStorage() {
        String type = configManager.getString("storage.type", "yaml").toLowerCase();
        if ("mysql".equals(type)) {
            try {
                storageManager = new MySQLStorage(this);
                storageManager.init();
                return;
            } catch (Exception e) {
                getLogger().warning("MySQL connection failed (" + e.getMessage()
                        + "). Falling back to YAML storage.");
            }
        }
        storageManager = new YamlStorage(this);
        storageManager.init();
    }

    private void initModules() {
        if (configManager.getBoolean("status.enabled", true)) {
            statusModule = new StatusModule(this);
            statusModule.init();
        }
        String liveStatsChannel = configManager.getString("live-stats.channel", "");
        if (configManager.getBoolean("live-stats.enabled", true)
                && !liveStatsChannel.isEmpty() && !liveStatsChannel.startsWith("YOUR_")) {
            liveStatsModule = new LiveStatsModule(this);
            liveStatsModule.init();
        }
        if (configManager.getBoolean("leaderboard.enabled", true)) {
            leaderboardModule = new LeaderboardModule(this);
            leaderboardModule.init();
        }
        if (configManager.getBoolean("tickets.enabled", true)) {
            ticketModule = new TicketModule(this);
            ticketModule.init();
        }
        if (configManager.getBoolean("linking.enabled", true)) {
            linkModule = new LinkModule(this);
            linkModule.init();
        }
        if (configManager.getBoolean("anti-raid.enabled", true)) {
            antiRaidModule = new AntiRaidModule(this);
            antiRaidModule.init();
        }
        if (configManager.getBoolean("performance.enabled", true)) {
            performanceModule = new PerformanceModule(this);
            performanceModule.init();
        }
        if (configManager.getBoolean("reaction-roles.enabled", true)) {
            reactionRoleModule = new ReactionRoleModule(this);
            reactionRoleModule.init();
        }

        embedBuilderModule = new EmbedBuilderModule(this);

        if (configManager.getBoolean("command-logger.enabled", true)) {
            commandLoggerModule = new CommandLoggerModule(this);
            commandLoggerModule.init();
        }

        String consoleChannelId = configManager.getString("channels.console", "");
        if (!consoleChannelId.isEmpty() && !consoleChannelId.startsWith("YOUR_")) {
            consoleModule = new ConsoleModule(this);
            consoleModule.init();
        }

        if (configManager.getBoolean("staff-chat.enabled", true)) {
            staffChatModule = new StaffChatModule(this);
            staffChatModule.init();
        }
        if (configManager.getBoolean("voice-status.enabled", true)) {
            voiceStatusModule = new VoiceStatusModule(this);
            voiceStatusModule.init();
        }

        followModule = new FollowModule(this);
        followModule.init();

        ZLogger.info(ZLogger.Category.MODULES, "Modules initialised.");
    }

    private void registerListeners() {
        if (!paperModern) {
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        }
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);

        if (paperModern) {
            getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
            getServer().getPluginManager().registerEvents(
                    new PaperStaffChatListener(this), this);
        }

        if (configManager.getBoolean("linking.required", false) && linkModule != null) {
            getServer().getPluginManager().registerEvents(new LinkEnforcementListener(this), this);
        }
    }

    private void registerCommands() {
        PluginCommand zd = getCommand("zdiscord");
        if (zd != null) {
            ZDiscordCommand executor = new ZDiscordCommand(this);
            zd.setExecutor(executor);
            zd.setTabCompleter(executor);
        }
        PluginCommand discord = getCommand("discord");
        if (discord != null) {
            discord.setExecutor(new DiscordCommand(this));
        }
        PluginCommand sc = getCommand("sc");
        if (sc != null) {
            sc.setExecutor(new StaffChatCommand(this));
        }
        PluginCommand confess = getCommand("confess");
        if (confess != null) {
            confess.setExecutor(new ConfessCommand(this));
        }
    }

    public void reload() {
        configManager.reload();
        messageManager.reload();
        ZLogger.init(getLogger(), configManager.getConfig());
        if (ZLogger.isDebugMode()) {
            ZLogger.info(ZLogger.Category.SYSTEM,
                    "Debug logging is on (logging.debug: true) - expect verbose output.");
        }

        if (statusModule != null) statusModule.reload();
        if (liveStatsModule != null) liveStatsModule.reload();
        if (leaderboardModule != null) leaderboardModule.reload();
        if (ticketModule != null) ticketModule.reload();
        if (linkModule != null) linkModule.reload();
        if (antiRaidModule != null) antiRaidModule.reload();
        if (performanceModule != null) performanceModule.reload();
        if (commandLoggerModule != null) commandLoggerModule.reload();
        if (staffChatModule != null) staffChatModule.reload();
        if (voiceStatusModule != null) voiceStatusModule.reload();
        if (followModule != null) followModule.reload();
        if (integrationModule != null) integrationModule.reload();

        if (botManager != null && botManager.isConnected()) {
            botManager.updateActivity();
        }
        ZLogger.info(ZLogger.Category.SYSTEM, "Configuration reloaded.");
    }

    public static ZDiscord getInstance() {
        return instance;
    }

    public PlatformAdapter getPlatformAdapter() {
        return platformAdapter;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public BotManager getBotManager() {
        return botManager;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public SlashCommandManager getSlashCommandManager() {
        return slashCommandManager;
    }

    public SetupCommand getSetupCommand() {
        return setupCommand;
    }

    public StatusModule getStatusModule() {
        return statusModule;
    }

    public LiveStatsModule getLiveStatsModule() {
        return liveStatsModule;
    }

    public LeaderboardModule getLeaderboardModule() {
        return leaderboardModule;
    }

    public TicketModule getTicketModule() {
        return ticketModule;
    }

    public LinkModule getLinkModule() {
        return linkModule;
    }

    public AntiRaidModule getAntiRaidModule() {
        return antiRaidModule;
    }

    public PerformanceModule getPerformanceModule() {
        return performanceModule;
    }

    public ReactionRoleModule getReactionRoleModule() {
        return reactionRoleModule;
    }

    public EmbedBuilderModule getEmbedBuilderModule() {
        return embedBuilderModule;
    }

    public CommandLoggerModule getCommandLoggerModule() {
        return commandLoggerModule;
    }

    public StaffChatModule getStaffChatModule() {
        return staffChatModule;
    }

    public VoiceStatusModule getVoiceStatusModule() {
        return voiceStatusModule;
    }

    public ConsoleModule getConsoleModule() {
        return consoleModule;
    }

    public FollowModule getFollowModule() {
        return followModule;
    }

    public ConfessionModule getConfessionModule() {
        return confessionModule;
    }

    public void debug(String message) {
        ZLogger.debug(ZLogger.Category.SYSTEM, message);
    }
}

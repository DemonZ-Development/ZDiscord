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
import dev.demonz.zdiscord.minecraft.commands.LinkCommand;
import dev.demonz.zdiscord.minecraft.commands.StaffChatCommand;
import dev.demonz.zdiscord.minecraft.commands.ZDiscordCommand;
import dev.demonz.zdiscord.minecraft.listeners.AdvancementListener;
import dev.demonz.zdiscord.minecraft.listeners.ChatListener;
import dev.demonz.zdiscord.minecraft.listeners.DeathListener;
import dev.demonz.zdiscord.minecraft.listeners.HalloweenHuntListener;
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
import dev.demonz.zdiscord.modules.HalloweenHunt;
import dev.demonz.zdiscord.modules.HalloweenModule;
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
    private volatile dev.demonz.zdiscord.modules.CraftyAIModule craftyAIModule;
    public dev.demonz.zdiscord.modules.CraftyAIModule getCraftyAIModule() { return craftyAIModule; }

    private static final String ASYNC_CHAT_EVENT = "io.papermc.paper.event.player.AsyncChatEvent";

    private static volatile ZDiscord instance;

    private PlatformAdapter platformAdapter;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private BotManager botManager;
    private WebhookManager webhookManager;
    private SlashCommandManager slashCommandManager;
    private SetupCommand setupCommand;
    private StorageManager storageManager;
    private boolean paperModern;

    private volatile StatusModule statusModule;
    private volatile LiveStatsModule liveStatsModule;
    private volatile LeaderboardModule leaderboardModule;
    private volatile TicketModule ticketModule;
    private volatile LinkModule linkModule;
    private volatile LinkModule retainedLinkModule;
    private volatile LeaderboardModule retainedLeaderboardModule;
    private volatile AntiRaidModule antiRaidModule;
    private volatile PerformanceModule performanceModule;
    private volatile ReactionRoleModule reactionRoleModule;
    private volatile EmbedBuilderModule embedBuilderModule;
    private volatile CommandLoggerModule commandLoggerModule;
    private volatile StaffChatModule staffChatModule;
    private volatile VoiceStatusModule voiceStatusModule;
    private volatile ConsoleModule consoleModule;
    private volatile FollowModule followModule;
    private volatile ConfessionModule confessionModule;
    private volatile IntegrationModule integrationModule;
    private volatile HalloweenModule halloweenModule;
    private HalloweenHunt halloweenHunt;
    private JoinQuitListener joinQuitListener;
    private UpdateChecker updateChecker;

    @Override
    @SuppressWarnings("deprecation")
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
        if (configManager.getBoolean("halloween.enabled", true)) {
            halloweenHunt = new HalloweenHunt(storageManager);
            halloweenModule = new HalloweenModule(this, halloweenHunt);
        }
        craftyAIModule = new dev.demonz.zdiscord.modules.CraftyAIModule(this);
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
            integrationModule = new IntegrationModule(this);
            integrationModule.init();
        }

        initModules();
        if (halloweenModule != null) halloweenModule.init();
        registerListeners();
        registerCommands();

        refreshApi();
        updateChecker = new UpdateChecker(this);

        long elapsed = System.currentTimeMillis() - start;
        StartupBanner.print(this, elapsed);
        platformAdapter.runTimer(() -> {
            if (joinQuitListener != null) {
                joinQuitListener.flushPlaytime();
            }
        }, 1200L, 1200L);
    }

    @Override
    public void onDisable() {
        if (halloweenModule != null) halloweenModule.shutdown();
        if (craftyAIModule != null) craftyAIModule.close();
        if (updateChecker != null) updateChecker.shutdown();
        ZLogger.info(ZLogger.Category.SYSTEM, "Shutting down ZDiscord...");

        ZDiscordProvider.unregister();
        getServer().getServicesManager().unregisterAll(this);

        if (integrationModule != null) integrationModule.shutdown();

        if (joinQuitListener != null) {
            joinQuitListener.flushPlaytime();
        }

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
        if (webhookManager != null) webhookManager.shutdown();
        if (botManager != null) botManager.shutdown();
        if (platformAdapter != null) platformAdapter.cancelAllTasks();
        if (storageManager != null) {
            storageManager.shutdown();
            ZLogger.info(ZLogger.Category.SYSTEM, "Storage flushed.");
        }

        ZLogger.info(ZLogger.Category.SYSTEM, "ZDiscord shut down.");
        instance = null;
    }

    public void greetHalloween(org.bukkit.entity.Player player) {
        if (halloweenModule != null) {
            halloweenModule.greetPlayer(player);
        }
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

    private <T> T reconcile(T current, boolean enabled, java.util.function.Supplier<T> factory,
                            java.util.function.Consumer<T> init, java.util.function.Consumer<T> reload,
                            java.util.function.Consumer<T> shutdown) {
        if (!enabled) {
            if (current != null) shutdown.accept(current);
            return null;
        }
        if (current == null) {
            current = factory.get();
            init.accept(current);
        } else if (reload != null) {
            reload.accept(current);
        }
        return current;
    }

    private boolean usableChannel(String path) {
        String id = configManager.getString(path, "");
        return !id.isBlank() && !id.startsWith("YOUR_");
    }

    private void initModules() {
        boolean online = botManager.isConnected();
        linkModule = reconcile(linkModule, configManager.getBoolean("linking.enabled", true),
                () -> retainedLinkModule == null ? (retainedLinkModule = new LinkModule(this)) : retainedLinkModule,
                LinkModule::init, LinkModule::reload, LinkModule::shutdown);
        leaderboardModule = reconcile(leaderboardModule, configManager.getBoolean("leaderboard.enabled", true),
                () -> retainedLeaderboardModule == null ? (retainedLeaderboardModule = new LeaderboardModule(this)) : retainedLeaderboardModule,
                LeaderboardModule::init, LeaderboardModule::reload, LeaderboardModule::shutdown);
        antiRaidModule = reconcile(antiRaidModule, configManager.getBoolean("anti-raid.enabled", true),
                () -> new AntiRaidModule(this), AntiRaidModule::init, null, AntiRaidModule::shutdown);
        statusModule = reconcile(statusModule, online && configManager.getBoolean("status.enabled", true),
                () -> new StatusModule(this), StatusModule::init, StatusModule::reload, StatusModule::shutdown);
        liveStatsModule = reconcile(liveStatsModule, online && configManager.getBoolean("live-stats.enabled", true) && usableChannel("live-stats.channel"),
                () -> new LiveStatsModule(this), LiveStatsModule::init, LiveStatsModule::reload, LiveStatsModule::shutdown);
        ticketModule = reconcile(ticketModule, online && configManager.getBoolean("tickets.enabled", true),
                () -> new TicketModule(this), TicketModule::init, null, TicketModule::shutdown);
        performanceModule = reconcile(performanceModule, online && configManager.getBoolean("performance.enabled", true),
                () -> new PerformanceModule(this), PerformanceModule::init, PerformanceModule::reload, PerformanceModule::shutdown);
        reactionRoleModule = reconcile(reactionRoleModule, online && configManager.getBoolean("reaction-roles.enabled", true),
                () -> new ReactionRoleModule(this), ReactionRoleModule::init, ReactionRoleModule::reload, ReactionRoleModule::shutdown);
        commandLoggerModule = reconcile(commandLoggerModule, online && configManager.getBoolean("command-logger.enabled", true),
                () -> new CommandLoggerModule(this), CommandLoggerModule::init, CommandLoggerModule::reload, CommandLoggerModule::shutdown);
        consoleModule = reconcile(consoleModule, online && usableChannel("channels.console"),
                () -> new ConsoleModule(this), ConsoleModule::init, null, ConsoleModule::shutdown);
        staffChatModule = reconcile(staffChatModule, online && configManager.getBoolean("staff-chat.enabled", true),
                () -> new StaffChatModule(this), StaffChatModule::init, StaffChatModule::reload, StaffChatModule::shutdown);
        voiceStatusModule = reconcile(voiceStatusModule, online && configManager.getBoolean("voice-status.enabled", true),
                () -> new VoiceStatusModule(this), VoiceStatusModule::init, VoiceStatusModule::reload, VoiceStatusModule::shutdown);
        if (followModule == null) followModule = new FollowModule(this);
        if (embedBuilderModule == null) embedBuilderModule = new EmbedBuilderModule(this);
        ZLogger.info(ZLogger.Category.MODULES, "Modules reconciled with configuration.");
    }

    public void refreshModules() {
        platformAdapter.runSync(this::initModules);
    }

    private void registerListeners() {
        if (!paperModern) {
            getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        }
        joinQuitListener = new JoinQuitListener(this);
        joinQuitListener.initOnlinePlayers();
        getServer().getPluginManager().registerEvents(joinQuitListener, this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new HalloweenHuntListener(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);

        if (paperModern) {
            getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
            getServer().getPluginManager().registerEvents(
                    new PaperStaffChatListener(this), this);
        }

        getServer().getPluginManager().registerEvents(new LinkEnforcementListener(this), this);
    }

    private void registerCommands() {
        PluginCommand zd = getCommand("zdiscord");
        if (zd != null) {
            ZDiscordCommand executor = new ZDiscordCommand(this);
            zd.setExecutor(executor);
            zd.setTabCompleter(executor);
        }
        PluginCommand link = getCommand("link");
        if (link != null) {
            link.setExecutor(new LinkCommand(this));
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
        if (craftyAIModule != null) craftyAIModule.reload();
        messageManager.reload();
        refreshApi();
        ZLogger.init(getLogger(), configManager.getConfig());
        if (ZLogger.isDebugMode()) {
            ZLogger.info(ZLogger.Category.SYSTEM,
                    "Debug logging is on (logging.debug: true) - expect verbose output.");
        }

        if (joinQuitListener != null) {
            joinQuitListener.flushPlaytime();
        }

        initModules();
        if (!botManager.isConnected()) {
            platformAdapter.runAsync(() -> {
                if (botManager.connect()) platformAdapter.runSync(() -> {
                    if (!isEnabled()) return;
                    if (webhookManager == null) webhookManager = new WebhookManager(this);
                    slashCommandManager.registerCommands();
                    initModules();
                    if (integrationModule == null) {
                        integrationModule = new IntegrationModule(this);
                        integrationModule.init();
                    }
                });
            });
        }
        if (integrationModule != null) integrationModule.reload();

        if (configManager.getBoolean("halloween.enabled", true)) {
            if (halloweenModule == null) {
                if (halloweenHunt == null) halloweenHunt = new HalloweenHunt(storageManager);
                halloweenModule = new HalloweenModule(this, halloweenHunt);
            }
            halloweenModule.reload();
        } else {
            if (halloweenModule != null) halloweenModule.shutdown();
            halloweenModule = null;
        }

        if (botManager != null && botManager.isConnected()) {
            botManager.updateActivity();
        }
        ZLogger.info(ZLogger.Category.SYSTEM, "Configuration reloaded.");
    }

    private void refreshApi() {
        if (!configManager.getBoolean("api.enabled", true)) {
            ZDiscordProvider.unregister();
            getServer().getServicesManager().unregisterAll(this);
        } else if (!ZDiscordProvider.isAvailable()) {
            ZDiscordProvider.register(new ZDiscordAPIImpl(this));
            getServer().getServicesManager().register(
                    ZDiscordAPI.class, ZDiscordProvider.get(), this, ServicePriority.Normal);
        }
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

    public IntegrationModule getIntegrationModule() {
        return integrationModule;
    }

    public HalloweenModule getHalloweenModule() {
        return halloweenModule;
    }

    public JoinQuitListener getJoinQuitListener() {
        return joinQuitListener;
    }

    public void debug(String message) {
        ZLogger.debug(ZLogger.Category.SYSTEM, message);
    }
}

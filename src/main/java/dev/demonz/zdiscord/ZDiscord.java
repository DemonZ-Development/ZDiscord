package dev.demonz.zdiscord;

import dev.demonz.zdiscord.config.ConfigManager;
import dev.demonz.zdiscord.config.MessageManager;
import dev.demonz.zdiscord.discord.BotManager;
import dev.demonz.zdiscord.discord.SlashCommandManager;
import dev.demonz.zdiscord.discord.WebhookManager;
import dev.demonz.zdiscord.minecraft.commands.DiscordCommand;
import dev.demonz.zdiscord.minecraft.commands.StaffChatCommand;
import dev.demonz.zdiscord.minecraft.commands.ZDiscordCommand;
import dev.demonz.zdiscord.minecraft.listeners.AdvancementListener;
import dev.demonz.zdiscord.minecraft.listeners.ChatListener;
import dev.demonz.zdiscord.minecraft.listeners.DeathListener;
import dev.demonz.zdiscord.minecraft.listeners.JoinQuitListener;
import dev.demonz.zdiscord.minecraft.listeners.LinkEnforcementListener;
import dev.demonz.zdiscord.modules.*;
import dev.demonz.zdiscord.platform.FoliaAdapter;
import dev.demonz.zdiscord.platform.PaperAdapter;
import dev.demonz.zdiscord.platform.PlatformAdapter;
import dev.demonz.zdiscord.platform.SpigotAdapter;
import dev.demonz.zdiscord.storage.MySQLStorage;
import dev.demonz.zdiscord.storage.StorageManager;
import dev.demonz.zdiscord.storage.YamlStorage;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ZDiscord - Premium Discord ↔ Minecraft Integration
 * Supports Paper, Folia, and Spigot
 *
 * @author DemonZ Development
 */
public class ZDiscord extends JavaPlugin {

    private static ZDiscord instance;

    private PlatformAdapter platformAdapter;
    private ConfigManager configManager;
    private MessageManager messageManager;
    private BotManager botManager;
    private WebhookManager webhookManager;
    private SlashCommandManager slashCommandManager;
    private StorageManager storageManager;

    // Modules
    private StatusModule statusModule;
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

    @Override
    public void onEnable() {
        instance = this;
        long start = System.currentTimeMillis();

        getLogger().info("");
        getLogger().info("");
        getServer().getConsoleSender().sendMessage("§b╔══════════════════════════════════════════════════╗");
        getServer().getConsoleSender().sendMessage("§b║                                                  ║");
        getServer().getConsoleSender()
                .sendMessage("§b║   §3███████╗§b██████╗ §3██╗§b███████╗ §3██████╗§b ██████╗ §3██████╗  §b║");
        getServer().getConsoleSender()
                .sendMessage("§b║   §3╚══███╔╝§b██╔══██╗§3██║§b██╔════╝§3██╔════╝§b██╔═══██╗§3██╔══██╗ §b║");
        getServer().getConsoleSender()
                .sendMessage("§b║     §3███╔╝ §b██║  ██║§3██║§b███████╗§3██║     §b██║   ██║§3██████╔╝ §b║");
        getServer().getConsoleSender()
                .sendMessage("§b║    §3███╔╝  §b██║  ██║§3██║§b╚════██║§3██║     §b██║   ██║§3██╔══██╗ §b║");
        getServer().getConsoleSender()
                .sendMessage("§b║   §3███████╗§b██████╔╝§3██║§b███████║§3╚██████╗§b╚██████╔╝§3██║  ██║ §b║");
        getServer().getConsoleSender()
                .sendMessage("§b║   §3╚══════╝§b╚═════╝ §3╚═╝§b╚══════╝ §3╚═════╝ §b╚═════╝ §3╚═╝  ╚═╝ §b║");
        getServer().getConsoleSender().sendMessage("§b║                                                  ║");
        getServer().getConsoleSender().sendMessage(
                "§b║  §fv" + getDescription().getVersion() + " §8│ §7Premium Discord Integration            §b║");
        getServer().getConsoleSender().sendMessage("§b║  §fDeveloped by §b§lDemonZ Development              §b║");
        getServer().getConsoleSender().sendMessage("§b║                                                  ║");
        getServer().getConsoleSender().sendMessage("§b╚══════════════════════════════════════════════════╝");
        getLogger().info("");

        // Detect platform
        detectPlatform();

        // Load configuration
        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        // Initialize bStats metrics
        new org.bstats.bukkit.Metrics(this, 29652);

        // Initialize storage
        initStorage();

        // Initialize Discord bot
        botManager = new BotManager(this);
        if (!botManager.connect()) {
            getLogger().severe("Failed to connect to Discord! Check your bot token in config.yml");
            getLogger().severe("Plugin will continue running but Discord features will be disabled.");
        } else {
            // Initialize webhook manager
            webhookManager = new WebhookManager(this);

            // Initialize slash commands
            slashCommandManager = new SlashCommandManager(this);
            slashCommandManager.registerCommands();

            // Initialize modules
            initModules();

            // Register JDA listeners for reconnection and ticket buttons
            botManager.getJda().addEventListener(new dev.demonz.zdiscord.discord.listeners.ReconnectListener(this));
            botManager.getJda().addEventListener(new dev.demonz.zdiscord.discord.listeners.TicketButtonListener(this));
        }

        // Schedule auto-save every 5 minutes
        platformAdapter.runAsyncTimer(() -> {
            if (storageManager != null) {
                storageManager.shutdown(); // flush all pending writes
                debug("Auto-save completed.");
            }
        }, 6000L, 6000L); // 300 seconds = 6000 ticks

        // Register Minecraft listeners
        registerListeners();

        // Register commands
        registerCommands();

        // Check for updates
        new dev.demonz.zdiscord.util.UpdateChecker(this);

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("");
        getServer().getConsoleSender().sendMessage("§8§m─────────────────────────────────────────");
        getServer().getConsoleSender().sendMessage("§a ✓ §fZDiscord §7enabled in §b" + elapsed + "ms");
        getServer().getConsoleSender().sendMessage("§a ✓ §fPlatform: §b" + platformAdapter.getPlatformName());
        getServer().getConsoleSender().sendMessage("§a ✓ §fVersion: §b" + getDescription().getVersion());
        getServer().getConsoleSender().sendMessage("§a ✓ §fAuthor: §b§lDemonZ Development");
        getServer().getConsoleSender().sendMessage("§8§m─────────────────────────────────────────");
        getLogger().info("");
    }

    @Override
    public void onDisable() {
        getLogger().info("");
        getServer().getConsoleSender().sendMessage("§c§m─────────────────────────────────────────");
        getServer().getConsoleSender().sendMessage("§c ✗ §fShutting down §bZDiscord§f...");

        // Shutdown modules
        if (statusModule != null)
            statusModule.shutdown();
        if (leaderboardModule != null)
            leaderboardModule.shutdown();
        if (ticketModule != null)
            ticketModule.shutdown();
        if (linkModule != null)
            linkModule.shutdown();
        if (antiRaidModule != null)
            antiRaidModule.shutdown();
        if (performanceModule != null)
            performanceModule.shutdown();
        if (reactionRoleModule != null)
            reactionRoleModule.shutdown();
        if (commandLoggerModule != null)
            commandLoggerModule.shutdown();
        if (staffChatModule != null)
            staffChatModule.shutdown();
        if (voiceStatusModule != null)
            voiceStatusModule.shutdown();
        if (consoleModule != null)
            consoleModule.shutdown();

        // Shutdown storage
        if (storageManager != null)
            storageManager.shutdown();

        // Shutdown webhook manager
        if (webhookManager != null)
            webhookManager.shutdown();

        // Shutdown Discord bot
        if (botManager != null)
            botManager.shutdown();

        getServer().getConsoleSender().sendMessage("§c ✗ §fAll modules shut down.");
        getServer().getConsoleSender()
                .sendMessage("§7 Thank you for using §b§lZDiscord §7by §b§lDemonZ Development§7!");
        getServer().getConsoleSender().sendMessage("§c§m─────────────────────────────────────────");
        getLogger().info("");

        instance = null;
    }

    private void detectPlatform() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            platformAdapter = new FoliaAdapter(this);
            getLogger().info("Detected Folia! Using region-aware scheduling.");
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
                platformAdapter = new PaperAdapter(this);
                getLogger().info("Detected Paper! Using Paper-optimized features.");
            } catch (ClassNotFoundException e2) {
                platformAdapter = new SpigotAdapter(this);
                getLogger().info("Detected Spigot! Using Bukkit scheduler.");
            }
        }
    }

    private void initStorage() {
        String type = configManager.getString("storage.type", "yaml").toLowerCase();

        if ("mysql".equals(type)) {
            try {
                storageManager = new MySQLStorage(this);
                storageManager.init();
            } catch (Exception e) {
                getLogger().warning("MySQL connection failed: " + e.getMessage());
                getLogger().warning("Falling back to YAML storage.");
                storageManager = new YamlStorage(this);
                storageManager.init();
            }
        } else {
            storageManager = new YamlStorage(this);
            storageManager.init();
        }
    }

    private void initModules() {
        if (configManager.getConfig().getBoolean("status.enabled", true)) {
            statusModule = new StatusModule(this);
            statusModule.init();
            getLogger().info("✓ Status Module enabled");
        }

        if (configManager.getConfig().getBoolean("leaderboard.enabled", true)) {
            leaderboardModule = new LeaderboardModule(this);
            leaderboardModule.init();
            getLogger().info("✓ Leaderboard Module enabled");
        }

        if (configManager.getConfig().getBoolean("tickets.enabled", true)) {
            ticketModule = new TicketModule(this);
            ticketModule.init();
            getLogger().info("✓ Ticket Module enabled");
        }

        if (configManager.getConfig().getBoolean("linking.enabled", true)) {
            linkModule = new LinkModule(this);
            linkModule.init();
            getLogger().info("✓ Account Linking Module enabled");
        }

        if (configManager.getConfig().getBoolean("anti-raid.enabled", true)) {
            antiRaidModule = new AntiRaidModule(this);
            antiRaidModule.init();
            getLogger().info("✓ Anti-Raid Module enabled");
        }

        if (configManager.getConfig().getBoolean("performance.enabled", true)) {
            performanceModule = new PerformanceModule(this);
            performanceModule.init();
            getLogger().info("✓ Performance Monitor enabled");
        }

        if (configManager.getConfig().getBoolean("reaction-roles.enabled", true)) {
            reactionRoleModule = new ReactionRoleModule(this);
            reactionRoleModule.init();
            getLogger().info("✓ Reaction Roles Module enabled");
        }

        embedBuilderModule = new EmbedBuilderModule(this);
        getLogger().info("✓ Embed Builder Module enabled");

        if (configManager.getConfig().getBoolean("command-logger.enabled", true)) {
            commandLoggerModule = new CommandLoggerModule(this);
            commandLoggerModule.init();
            getLogger().info("✓ Command Logger Module enabled");
        }

        // Console output streaming
        String consoleChannelId = configManager.getString("channels.console", "");
        if (consoleChannelId != null && !consoleChannelId.isEmpty() && !consoleChannelId.startsWith("YOUR_")) {
            consoleModule = new ConsoleModule(this);
            consoleModule.init();
            getLogger().info("✓ Console Module enabled");
        }

        if (configManager.getConfig().getBoolean("staff-chat.enabled", true)) {
            staffChatModule = new StaffChatModule(this);
            staffChatModule.init();
            getLogger().info("✓ Staff Chat Module enabled");
        }

        if (configManager.getConfig().getBoolean("voice-status.enabled", true)) {
            voiceStatusModule = new VoiceStatusModule(this);
            voiceStatusModule.init();
            getLogger().info("✓ Voice Status Module enabled");
        }
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);

        if (configManager.getBoolean("linking.required", false)) {
            getServer().getPluginManager().registerEvents(new LinkEnforcementListener(this), this);
            getLogger().info("✓ Link-to-Join enforcement enabled");
        }
    }

    private void registerCommands() {
        getCommand("zdiscord").setExecutor(new ZDiscordCommand(this));
        getCommand("discord").setExecutor(new DiscordCommand(this));
        if (getCommand("sc") != null) {
            getCommand("sc").setExecutor(new StaffChatCommand(this));
        }
    }

    public void reload() {
        configManager.reload();
        messageManager.reload();

        // Reload all active modules so they re-read config values
        if (statusModule != null)
            statusModule.reload();
        if (leaderboardModule != null)
            leaderboardModule.reload();
        if (ticketModule != null)
            ticketModule.reload();
        if (linkModule != null)
            linkModule.reload();
        if (antiRaidModule != null)
            antiRaidModule.reload();
        if (performanceModule != null)
            performanceModule.reload();
        if (commandLoggerModule != null)
            commandLoggerModule.reload();
        if (staffChatModule != null)
            staffChatModule.reload();
        if (voiceStatusModule != null)
            voiceStatusModule.reload();

        // Update bot activity
        if (botManager != null && botManager.isConnected()) {
            botManager.updateActivity();
        }

        getLogger().info("ZDiscord configuration reloaded!");
    }

    // ─── Getters ─────────────────────────────────────

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

    public StatusModule getStatusModule() {
        return statusModule;
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

    public void debug(String message) {
        if (configManager.getConfig().getBoolean("misc.debug", false)) {
            getLogger().log(Level.INFO, "[DEBUG] " + message);
        }
    }
}

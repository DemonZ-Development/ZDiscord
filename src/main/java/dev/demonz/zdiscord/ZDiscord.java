/*
 * Copyright 2024 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.demonz.zdiscord;

import dev.demonz.zdiscord.config.ConfigManager;
import dev.demonz.zdiscord.config.MessageManager;
import dev.demonz.zdiscord.discord.BotManager;
import dev.demonz.zdiscord.discord.SetupCommand;
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
import dev.demonz.zdiscord.minecraft.listeners.PaperChatListener;
import dev.demonz.zdiscord.modules.AntiRaidModule;
import dev.demonz.zdiscord.modules.CommandLoggerModule;
import dev.demonz.zdiscord.modules.ConsoleModule;
import dev.demonz.zdiscord.modules.EmbedBuilderModule;
import dev.demonz.zdiscord.modules.LeaderboardModule;
import dev.demonz.zdiscord.modules.LinkModule;
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
import dev.demonz.zdiscord.util.UpdateChecker;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * ZDiscord entry point.
 *
 * <p>Supports Paper, Folia, and Spigot via the {@link PlatformAdapter}
 * abstraction. Module behaviour is opt-in via {@code config.yml} flags.</p>
 */
public class ZDiscord extends JavaPlugin {

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

        detectPlatform();

        getLogger().info("Starting ZDiscord v" + getDescription().getVersion()
                + " on " + platformAdapter.getPlatformName());

        configManager = new ConfigManager(this);
        messageManager = new MessageManager(this);

        initStorage();

        new org.bstats.bukkit.Metrics(this, 29652);

        botManager = new BotManager(this);
        slashCommandManager = new SlashCommandManager(this);
        setupCommand = new SetupCommand(this);
        if (!botManager.connect()) {
            getLogger().severe("Failed to connect to Discord. Check bot.token and bot.guild-id in config.yml.");
            getLogger().severe("The plugin will continue to load, but Discord features are disabled.");
        } else {
            webhookManager = new WebhookManager(this);
            slashCommandManager.registerCommands();
            initModules();
        }

        registerListeners();
        registerCommands();

        if (configManager.getBoolean("misc.update-checker", true)) {
            new UpdateChecker(this);
        }

        long elapsed = System.currentTimeMillis() - start;
        getLogger().info("ZDiscord v" + getDescription().getVersion()
                + " enabled in " + elapsed + "ms on " + platformAdapter.getPlatformName());
    }

    @Override
    public void onDisable() {
        getLogger().info("Shutting down ZDiscord...");

        if (statusModule != null) statusModule.shutdown();
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

        if (storageManager != null) storageManager.shutdown();
        if (webhookManager != null) webhookManager.shutdown();
        if (botManager != null) botManager.shutdown();

        instance = null;
    }

    private void detectPlatform() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            platformAdapter = new FoliaAdapter(this);
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
                platformAdapter = new PaperAdapter(this);
                paperModern = true;
            } catch (ClassNotFoundException e2) {
                platformAdapter = new SpigotAdapter(this);
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
                getLogger().warning("MySQL connection failed (" + e.getMessage()
                        + "). Falling back to YAML storage.");
                storageManager = new YamlStorage(this);
                storageManager.init();
            }
        } else {
            storageManager = new YamlStorage(this);
            storageManager.init();
        }
    }

    private void initModules() {
        if (configManager.getBoolean("status.enabled", true)) {
            statusModule = new StatusModule(this);
            statusModule.init();
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

        getLogger().info("Modules initialised.");
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChatListener(this), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new DeathListener(this), this);
        getServer().getPluginManager().registerEvents(new AdvancementListener(this), this);

        if (paperModern) {
            getServer().getPluginManager().registerEvents(new PaperChatListener(this), this);
        }

        if (configManager.getBoolean("linking.required", false) && linkModule != null) {
            getServer().getPluginManager().registerEvents(new LinkEnforcementListener(this), this);
        }
    }

    private void registerCommands() {
        org.bukkit.command.PluginCommand zd = getCommand("zdiscord");
        if (zd != null) {
            ZDiscordCommand executor = new ZDiscordCommand(this);
            zd.setExecutor(executor);
            zd.setTabCompleter(executor);
        }
        org.bukkit.command.PluginCommand discord = getCommand("discord");
        if (discord != null) {
            discord.setExecutor(new DiscordCommand(this));
        }
        org.bukkit.command.PluginCommand sc = getCommand("sc");
        if (sc != null) {
            StaffChatCommand executor = new StaffChatCommand(this);
            sc.setExecutor(executor);
        }
    }

    public void reload() {
        configManager.reload();
        messageManager.reload();

        if (statusModule != null) statusModule.reload();
        if (leaderboardModule != null) leaderboardModule.reload();
        if (ticketModule != null) ticketModule.reload();
        if (linkModule != null) linkModule.reload();
        if (antiRaidModule != null) antiRaidModule.reload();
        if (performanceModule != null) performanceModule.reload();
        if (commandLoggerModule != null) commandLoggerModule.reload();
        if (staffChatModule != null) staffChatModule.reload();
        if (voiceStatusModule != null) voiceStatusModule.reload();

        if (botManager != null && botManager.isConnected()) {
            botManager.updateActivity();
        }
        getLogger().info("Configuration reloaded.");
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
        if (configManager.getBoolean("misc.debug", false)) {
            getLogger().log(Level.INFO, "[debug] " + message);
        }
    }
}

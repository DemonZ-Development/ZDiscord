package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Interactive setup wizard using Discord menus, buttons, and dropdowns.
 * Professional setup experience — no manual command typing required.
 */
public class SetupCommand extends ListenerAdapter {

    private final ZDiscord plugin;

    private static final Color BRAND = Color.decode("#5865F2");
    private static final Color SUCCESS = Color.decode("#2ECC71");
    private static final Color WARN = Color.decode("#F1C40F");

    private static final Map<String, ModuleInfo> MODULES = new LinkedHashMap<>();

    static {
        MODULES.put("chat", new ModuleInfo("💬 Chat Bridge", "channels.chat",
                "Syncs Minecraft ↔ Discord chat messages in real-time"));
        MODULES.put("status", new ModuleInfo("📊 Live Status", "channels.status",
                "Auto-updating server status panel with player count, TPS, memory"));
        MODULES.put("events", new ModuleInfo("📢 Events", "channels.events",
                "Join, quit, death, and advancement events"));
        MODULES.put("console", new ModuleInfo("🖥️ Console", "channels.console",
                "Server console output streamed to Discord"));
        MODULES.put("tickets", new ModuleInfo("🎫 Tickets", "channels.ticket-category",
                "Support ticket system with private channels"));
        MODULES.put("staffchat", new ModuleInfo("👥 Staff Chat", "staff-chat.channel",
                "Private staff communication bridge"));
        MODULES.put("cmdlog", new ModuleInfo("🔒 Command Logger", "command-logger.channel",
                "Logs dangerous command executions"));
        MODULES.put("performance", new ModuleInfo("📈 Performance", "channels.performance",
                "TPS & memory alerts when thresholds are exceeded"));
        MODULES.put("achievements", new ModuleInfo("🏆 Achievements", "channels.achievements",
                "Player advancement announcements"));
    }

    /** Valid module names for autocomplete */
    private static final List<String> MODULE_NAMES = new ArrayList<>(MODULES.keySet());

    public SetupCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    // ═══════════════════════════════════════════════════
    // SLASH COMMAND — /setup
    // ═══════════════════════════════════════════════════

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("setup"))
            return;

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ You need **Administrator** permission to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        // Check if module option was provided (legacy support)
        var moduleOption = event.getOption("module");
        var channelOption = event.getOption("channel");

        if (moduleOption != null && channelOption != null) {
            // Legacy quick-setup path
            String module = moduleOption.getAsString();
            TextChannel target = channelOption.getAsChannel().asTextChannel();
            quickSetup(event, module, target);
            return;
        }

        // Show interactive wizard panel
        showWizardPanel(event);
    }

    // ═══════════════════════════════════════════════════
    // AUTOCOMPLETE — /setup module:
    // ═══════════════════════════════════════════════════

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!event.getName().equals("setup"))
            return;
        if (!event.getFocusedOption().getName().equals("module"))
            return;

        String typed = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = MODULE_NAMES.stream()
                .filter(name -> name.startsWith(typed))
                .map(name -> {
                    ModuleInfo info = MODULES.get(name);
                    String label = info.emoji + " " + name;
                    return new Command.Choice(label, name);
                })
                .limit(25)
                .collect(Collectors.toList());

        event.replyChoices(choices).queue();
    }

    // ═══════════════════════════════════════════════════
    // WIZARD PANEL — Main embed with module dropdown
    // ═══════════════════════════════════════════════════

    private void showWizardPanel(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildIcon = guild != null && guild.getIconUrl() != null
                ? guild.getIconUrl() + "?size=128"
                : event.getJDA().getSelfUser().getEffectiveAvatarUrl();

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        // Count configured modules
        int configured = 0;
        for (ModuleInfo info : MODULES.values()) {
            String val = plugin.getConfigManager().getString(info.configPath, "");
            if (isSet(val))
                configured++;
        }

        String progressBar = buildProgressBar(configured, MODULES.size());

        // Build configuration status
        StringBuilder statusLines = new StringBuilder();
        for (Map.Entry<String, ModuleInfo> entry : MODULES.entrySet()) {
            ModuleInfo info = entry.getValue();
            String val = plugin.getConfigManager().getString(info.configPath, "");
            if (isSet(val)) {
                statusLines.append("┃ 🟢 ").append(info.emoji).append(" **").append(entry.getKey())
                        .append("** → <#").append(val).append(">\n");
            } else {
                statusLines.append("┃ ⚫ ").append(info.emoji).append(" ~~").append(entry.getKey())
                        .append("~~ → *Not configured*\n");
            }
        }

        EmbedBuilder panel = new EmbedBuilder()
                .setAuthor("ZDiscord Setup Wizard", null, guildIcon)
                .setTitle("⚡ Configure Your Server Integration")
                .setDescription(
                        "Select a module from the dropdown below to configure it.\n" +
                                "Each module connects a feature to a Discord channel.\n\n" +
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                .setColor(BRAND)
                .setThumbnail(guildIcon)
                .addField("📡 Connection", "🟢 **Online** — " + online + "/" + max + " players", true)
                .addField("⚙️ Progress", configured + "/" + MODULES.size() + " modules", true)
                .addField("📊 Setup Status", progressBar, true)
                .addField("┏━━━━━━━━━ Module Configuration ━━━━━━━━━┓",
                        statusLines.toString() +
                                "┗━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━┛",
                        false)
                .setFooter("ZDiscord v1.0.0-beta • DemonZ Development")
                .setTimestamp(Instant.now());

        // Build module dropdown
        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create("zdiscord_setup_module")
                .setPlaceholder("📦 Select a module to configure...")
                .setMinValues(1)
                .setMaxValues(1);

        for (Map.Entry<String, ModuleInfo> entry : MODULES.entrySet()) {
            ModuleInfo info = entry.getValue();
            String val = plugin.getConfigManager().getString(info.configPath, "");
            String status = isSet(val) ? "✅ Configured" : "⚫ Not set";
            menuBuilder.addOption(info.emoji + " " + capitalize(entry.getKey()),
                    entry.getKey(),
                    status + " — " + info.description,
                    Emoji.fromUnicode(info.emoji.substring(0, info.emoji.length() > 2 ? 2 : info.emoji.length())));
        }

        event.replyEmbeds(panel.build())
                .addActionRow(menuBuilder.build())
                .setEphemeral(false)
                .queue();
    }

    // ═══════════════════════════════════════════════════
    // MODULE SELECTION — StringSelectMenu handler
    // ═══════════════════════════════════════════════════

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("zdiscord_setup_module"))
            return;

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Only administrators can configure modules.").setEphemeral(true).queue();
            return;
        }

        String module = event.getValues().get(0);
        ModuleInfo info = MODULES.get(module);
        if (info == null) {
            event.reply("❌ Unknown module.").setEphemeral(true).queue();
            return;
        }

        // Show channel selection prompt
        EmbedBuilder prompt = new EmbedBuilder()
                .setTitle(info.emoji + " Configure " + capitalize(module))
                .setDescription(info.description + "\n\n" +
                        "**Select a channel** from the dropdown below to link this module.")
                .setColor(BRAND)
                .setFooter("Step 1/2 • Select a channel")
                .setTimestamp(Instant.now());

        EntitySelectMenu channelMenu = EntitySelectMenu.create("zdiscord_setup_channel:" + module,
                EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("#️⃣ Select a channel...")
                .setMinValues(1)
                .setMaxValues(1)
                .build();

        event.replyEmbeds(prompt.build())
                .addActionRow(channelMenu)
                .setEphemeral(true)
                .queue();
    }

    // ═══════════════════════════════════════════════════
    // CHANNEL SELECTION — EntitySelectMenu handler
    // ═══════════════════════════════════════════════════

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String componentId = event.getComponentId();

        if (componentId.startsWith("zdiscord_setup_channel:")) {
            handleChannelSelect(event);
        } else if (componentId.equals("zdiscord_setup_support_role")) {
            handleSupportRoleSelect(event);
        }
    }

    private void handleChannelSelect(EntitySelectInteractionEvent event) {
        String module = event.getComponentId().replace("zdiscord_setup_channel:", "");
        ModuleInfo info = MODULES.get(module);
        if (info == null)
            return;

        var selectedChannel = event.getMentions().getChannels().get(0);
        if (!(selectedChannel instanceof TextChannel)) {
            event.reply("❌ Please select a **text channel**.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = (TextChannel) selectedChannel;

        // Save to config
        saveToConfig(info.configPath, channel.getId());

        // Special handling per module
        switch (module) {
            case "status":
                handleStatusSetup(event, channel);
                return;

            case "tickets":
                handleTicketSetup(event, channel);
                return;

            default:
                // Standard channel setup — save and confirm
                postActivationNotice(channel, info, event.getUser().getName());

                EmbedBuilder confirm = new EmbedBuilder()
                        .setTitle("✅ " + info.emoji + " " + capitalize(module) + " Configured!")
                        .setDescription("Module has been linked to " + channel.getAsMention() + ".")
                        .setColor(SUCCESS)
                        .setFooter("ZDiscord • Module configured successfully")
                        .setTimestamp(Instant.now());

                event.replyEmbeds(confirm.build()).setEphemeral(true).queue();
                break;
        }
    }

    // ═══════════════════════════════════════════════════
    // STATUS MODULE SETUP — Posts live status panel
    // ═══════════════════════════════════════════════════

    private void handleStatusSetup(EntitySelectInteractionEvent event, TextChannel channel) {
        Guild guild = event.getGuild();
        String guildIcon = guild != null && guild.getIconUrl() != null
                ? guild.getIconUrl() + "?size=128"
                : event.getJDA().getSelfUser().getEffectiveAvatarUrl();

        String serverIp = plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com");
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double[] tps = dev.demonz.zdiscord.util.TPSUtil.getTPS();
        Runtime rt = Runtime.getRuntime();
        long usedMb = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long maxMb = rt.maxMemory() / 1024 / 1024;
        int memPct = maxMb > 0 ? (int) ((usedMb * 100.0) / maxMb) : 0;

        String tpsEmoji = tps[0] >= 18 ? "🟢" : tps[0] >= 15 ? "🟡" : "🔴";
        String memEmoji = memPct < 70 ? "🟢" : memPct < 85 ? "🟡" : "🔴";

        int barLen = 20;
        int filled = max > 0 ? (int) ((online / (double) max) * barLen) : 0;
        String bar = "█".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barLen - filled));

        String playerList = online > 0
                ? Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName).sorted().limit(20)
                        .collect(Collectors.joining(", "))
                : "*No players online*";

        EmbedBuilder status = new EmbedBuilder()
                .setTitle("🎮 Server Status")
                .setColor(BRAND)
                .setThumbnail(guildIcon)
                .addField("📊 Status", "🟢 **Online**", true)
                .addField("🌐 Server IP", "`" + serverIp + "`", true)
                .addField("\u200B", "\u200B", true)
                .addField("👥 Players [" + online + "/" + max + "]", "`" + bar + "`", false)
                .addField("🎮 Online", playerList, false)
                .addField(tpsEmoji + " TPS", String.format("%.1f / 20.0", tps[0]), true)
                .addField(memEmoji + " Memory", usedMb + "MB / " + maxMb + "MB (" + memPct + "%)", true)
                .setFooter("ZDiscord • Auto-updates every "
                        + plugin.getConfigManager().getInt("status.update-interval", 30) + "s")
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(status.build()).queue(
                msg -> {
                    saveToConfig("channels.status-message", msg.getId());

                    EmbedBuilder confirm = new EmbedBuilder()
                            .setTitle("✅ 📊 Live Status Configured!")
                            .setDescription("Status panel posted in " + channel.getAsMention() + ".\n" +
                                    "It will auto-update every " +
                                    plugin.getConfigManager().getInt("status.update-interval", 30) + " seconds.")
                            .setColor(SUCCESS)
                            .setTimestamp(Instant.now());

                    event.replyEmbeds(confirm.build()).setEphemeral(true).queue();
                },
                err -> event.reply("❌ Failed to send status panel: " + err.getMessage())
                        .setEphemeral(true).queue());
    }

    // ═══════════════════════════════════════════════════
    // TICKET SETUP — Channel → then ask for support role
    // ═══════════════════════════════════════════════════

    private void handleTicketSetup(EntitySelectInteractionEvent event, TextChannel channel) {
        // Save ticket category (parent of the selected channel)
        if (channel.getParentCategory() != null) {
            saveToConfig("channels.ticket-category", channel.getParentCategory().getId());
        }

        // Now ask for support role
        EmbedBuilder rolePrompt = new EmbedBuilder()
                .setTitle("🎫 Ticket Setup — Step 2")
                .setDescription("Great! Tickets will be created in **" + channel.getParentCategoryIdLong() + "**.\n\n" +
                        "Now **select the support role** that should have access to tickets.\n" +
                        "Staff with this role will be able to view, claim, and close tickets.")
                .setColor(BRAND)
                .setFooter("Step 2/3 • Select support role")
                .setTimestamp(Instant.now());

        EntitySelectMenu roleMenu = EntitySelectMenu.create("zdiscord_setup_support_role",
                EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("🛡️ Select the support role...")
                .setMinValues(1)
                .setMaxValues(1)
                .build();

        event.replyEmbeds(rolePrompt.build())
                .addActionRow(roleMenu)
                .setEphemeral(true)
                .queue();
    }

    private void handleSupportRoleSelect(EntitySelectInteractionEvent event) {
        var role = event.getMentions().getRoles().get(0);

        // Save support role to config
        saveToConfig("tickets.support-roles", Collections.singletonList(role.getId()));

        // Now post the ticket panel in the ticket channel
        String ticketCategoryId = plugin.getConfigManager().getString("channels.ticket-category", "");
        Guild guild = event.getGuild();

        // Find a suitable channel to post the ticket panel
        // Use the first text channel in the ticket category, or the channel where setup
        // was done
        TextChannel panelChannel = null;
        if (guild != null && !ticketCategoryId.isEmpty()) {
            var category = guild.getCategoryById(ticketCategoryId);
            if (category != null) {
                var channels = category.getTextChannels();
                if (!channels.isEmpty()) {
                    panelChannel = channels.get(0);
                }
            }
        }

        if (panelChannel == null) {
            panelChannel = event.getChannel().asTextChannel();
        }

        // Post the professional ticket panel
        postTicketPanel(panelChannel);

        EmbedBuilder confirm = new EmbedBuilder()
                .setTitle("✅ 🎫 Ticket System Configured!")
                .setDescription(
                        "**Support Role:** " + role.getAsMention() + "\n" +
                                "**Ticket Panel:** Posted in " + panelChannel.getAsMention() + "\n\n" +
                                "Users can now click the button to create support tickets!")
                .setColor(SUCCESS)
                .setFooter("ZDiscord • Ticket system ready")
                .setTimestamp(Instant.now());

        event.replyEmbeds(confirm.build()).setEphemeral(true).queue();
    }

    /**
     * Posts a professional ticket creation panel with a button.
     */
    private void postTicketPanel(TextChannel channel) {
        EmbedBuilder ticket = new EmbedBuilder()
                .setTitle("🎫 Support Center")
                .setDescription(
                        "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                                "Need help? Our support team is here for you!\n" +
                                "Click the button below to open a private support ticket.\n\n" +
                                "**What happens next?**\n" +
                                "┃ 📩 A private channel is created for you\n" +
                                "┃ 👥 Our support team is notified instantly\n" +
                                "┃ 🔒 Only you and staff can see the channel\n\n" +
                                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                                "**📌 Before opening a ticket:**\n" +
                                "> • Clearly describe your issue\n" +
                                "> • Include relevant details (IGN, screenshots)\n" +
                                "> • Check if your question is answered in FAQ\n" +
                                "> • One ticket per issue please")
                .setColor(BRAND)
                .setFooter("ZDiscord • Ticket System • Powered by DemonZ Development")
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(ticket.build())
                .addActionRow(
                        Button.success("zdiscord_create_ticket", "Create Ticket")
                                .withEmoji(Emoji.fromUnicode("📩")))
                .queue();
    }

    // ═══════════════════════════════════════════════════
    // TICKET CREATE BUTTON — zdiscord_create_ticket
    // ═══════════════════════════════════════════════════

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().equals("zdiscord_create_ticket"))
            return;

        if (plugin.getTicketModule() == null) {
            event.reply("❌ Ticket system is currently disabled.").setEphemeral(true).queue();
            return;
        }

        plugin.getTicketModule().createTicket(event.getUser(), "General Support", null);
        event.reply("✅ Your support ticket has been created! Check the new channel.").setEphemeral(true).queue();
    }

    // ═══════════════════════════════════════════════════
    // LEGACY QUICK SETUP — /setup module:x channel:#y
    // ═══════════════════════════════════════════════════

    private void quickSetup(SlashCommandInteractionEvent event, String module, TextChannel target) {
        ModuleInfo info = MODULES.get(module);
        if (info == null) {
            event.reply("❌ Unknown module: `" + module + "`\n" +
                    "Available: " + String.join(", ", MODULE_NAMES)).setEphemeral(true).queue();
            return;
        }

        saveToConfig(info.configPath, target.getId());

        if (module.equals("status")) {
            // For status, delegate to the full setup
            handleSetupStatusLegacy(event, target);
            return;
        }

        if (module.equals("tickets")) {
            if (target.getParentCategory() != null) {
                saveToConfig("channels.ticket-category", target.getParentCategory().getId());
            }
            postTicketPanel(target);
        }

        postActivationNotice(target, info, event.getUser().getName());

        event.replyEmbeds(new EmbedBuilder()
                .setTitle("✅ " + info.emoji + " " + capitalize(module) + " Configured!")
                .setDescription("Linked to " + target.getAsMention())
                .setColor(SUCCESS)
                .setTimestamp(Instant.now()).build()).setEphemeral(true).queue();
    }

    private void handleSetupStatusLegacy(SlashCommandInteractionEvent event, TextChannel target) {
        saveToConfig("channels.status", target.getId());

        Guild guild = event.getGuild();
        String guildIcon = guild != null && guild.getIconUrl() != null
                ? guild.getIconUrl() + "?size=128"
                : event.getJDA().getSelfUser().getEffectiveAvatarUrl();

        String serverIp = plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com");
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double[] tps = dev.demonz.zdiscord.util.TPSUtil.getTPS();

        EmbedBuilder status = new EmbedBuilder()
                .setTitle("🎮 Server Status")
                .setColor(BRAND)
                .setThumbnail(guildIcon)
                .addField("📊 Status", "🟢 **Online**", true)
                .addField("🌐 Server IP", "`" + serverIp + "`", true)
                .addField("👥 Players", online + "/" + max, true)
                .addField("⚡ TPS", String.format("%.1f", tps[0]), true)
                .setFooter("ZDiscord • Auto-updates every "
                        + plugin.getConfigManager().getInt("status.update-interval", 30) + "s")
                .setTimestamp(Instant.now());

        target.sendMessageEmbeds(status.build()).queue(
                msg -> {
                    saveToConfig("channels.status-message", msg.getId());
                    event.replyEmbeds(new EmbedBuilder()
                            .setTitle("✅ 📊 Status Configured!")
                            .setDescription("Live status in " + target.getAsMention())
                            .setColor(SUCCESS).setTimestamp(Instant.now()).build())
                            .setEphemeral(true).queue();
                },
                err -> event.reply("❌ Failed: " + err.getMessage()).setEphemeral(true).queue());
    }

    // ═══════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════

    private void postActivationNotice(TextChannel channel, ModuleInfo info, String username) {
        channel.sendMessageEmbeds(new EmbedBuilder()
                .setTitle("⚡ " + info.emoji + " Module Activated")
                .setDescription(
                        "This channel is now linked to **ZDiscord** for " + info.description.toLowerCase() + ".")
                .setColor(BRAND)
                .setFooter("Configured by " + username)
                .setTimestamp(Instant.now()).build())
                .queue(s -> {
                }, err -> plugin.debug("Activation notice failed: " + err.getMessage()));
    }

    private void saveToConfig(String path, String value) {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            config.set(path, value);
            config.save(configFile);
            plugin.getConfigManager().reload();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    private void saveToConfig(String path, List<String> values) {
        try {
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            config.set(path, values);
            config.save(configFile);
            plugin.getConfigManager().reload();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save config: " + e.getMessage());
        }
    }

    private String buildProgressBar(int current, int total) {
        int barLen = 9;
        int filled = total > 0 ? (int) ((current / (double) total) * barLen) : 0;
        return "▓".repeat(Math.max(0, filled)) + "░".repeat(Math.max(0, barLen - filled))
                + " " + Math.round((current / (double) total) * 100) + "%";
    }

    private boolean isSet(String value) {
        return value != null && !value.isEmpty() && !value.startsWith("YOUR_");
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ═══════════════════════════════════════════════════
    // MODULE INFO DATA CLASS
    // ═══════════════════════════════════════════════════

    private static class ModuleInfo {
        final String emoji;
        final String configPath;
        final String description;

        ModuleInfo(String emoji, String configPath, String description) {
            this.emoji = emoji;
            this.configPath = configPath;
            this.description = description;
        }
    }
}

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

package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.EmbedUtil;
import dev.demonz.zdiscord.util.StatusEmbedBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implements the {@code /setup} slash command — an interactive wizard
 * that configures channel IDs and posts setup panels without requiring
 * manual file editing.
 */
public class SetupCommand extends ListenerAdapter {

    private final ZDiscord plugin;

    private static final String PANEL_BUTTON_ID = "zdiscord_create_ticket";
    private static final String MODULE_MENU_ID = "zdiscord_setup_module";
    private static final String SUPPORT_ROLE_MENU_ID = "zdiscord_setup_support_role";
    private static final String CHANNEL_MENU_PREFIX = "zdiscord_setup_channel:";

    private static final Map<String, ModuleInfo> MODULES = new LinkedHashMap<>();

    static {
        MODULES.put("chat", new ModuleInfo("Chat Bridge", "channels.chat",
                "Syncs Minecraft and Discord chat in real time."));
        MODULES.put("status", new ModuleInfo("Live Status", "channels.status",
                "Auto-updating server status panel."));
        MODULES.put("events", new ModuleInfo("Events", "channels.events",
                "Join, quit, death, and advancement events."));
        MODULES.put("console", new ModuleInfo("Console", "channels.console",
                "Server console output streamed to Discord."));
        MODULES.put("tickets", new ModuleInfo("Tickets", "channels.ticket-category",
                "Support ticket system with private channels."));
        MODULES.put("staffchat", new ModuleInfo("Staff Chat", "staff-chat.channel",
                "Private staff communication bridge."));
        MODULES.put("cmdlog", new ModuleInfo("Command Logger", "command-logger.channel",
                "Logs dangerous command executions."));
        MODULES.put("performance", new ModuleInfo("Performance", "channels.performance",
                "TPS and memory alerts when thresholds are exceeded."));
        MODULES.put("achievements", new ModuleInfo("Achievements", "channels.achievements",
                "Player advancement announcements."));
    }

    private static final List<String> MODULE_NAMES = new ArrayList<>(MODULES.keySet());

    public SetupCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!"setup".equals(event.getName())) {
            return;
        }
        if (event.getMember() == null
                || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("You need **Administrator** permission to use this command.")
                    .setEphemeral(true).queue();
            return;
        }

        var moduleOption = event.getOption("module");
        var channelOption = event.getOption("channel");
        if (moduleOption != null && channelOption != null) {
            String module = moduleOption.getAsString();
            TextChannel target = channelOption.getAsChannel().asTextChannel();
            quickSetup(event, module, target);
            return;
        }
        showWizardPanel(event);
    }

    @Override
    public void onCommandAutoCompleteInteraction(CommandAutoCompleteInteractionEvent event) {
        if (!"setup".equals(event.getName())) {
            return;
        }
        if (!"module".equals(event.getFocusedOption().getName())) {
            return;
        }
        String typed = event.getFocusedOption().getValue().toLowerCase();
        List<Command.Choice> choices = MODULE_NAMES.stream()
                .filter(name -> name.startsWith(typed))
                .map(name -> new Command.Choice(name, name))
                .limit(25)
                .collect(Collectors.toList());
        event.replyChoices(choices).queue();
    }

    private void showWizardPanel(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        String guildIcon = guild != null && guild.getIconUrl() != null
                ? guild.getIconUrl() + "?size=128"
                : event.getJDA().getSelfUser().getEffectiveAvatarUrl();

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        int configured = 0;
        for (ModuleInfo info : MODULES.values()) {
            if (isSet(plugin.getConfigManager().getString(info.configPath, ""))) {
                configured++;
            }
        }

        StringBuilder statusLines = new StringBuilder();
        for (Map.Entry<String, ModuleInfo> entry : MODULES.entrySet()) {
            ModuleInfo info = entry.getValue();
            String val = plugin.getConfigManager().getString(info.configPath, "");
            if (isSet(val)) {
                statusLines.append("- [Configured] **").append(entry.getKey())
                        .append("** -> <#").append(val).append(">\n");
            } else {
                statusLines.append("- [Not set] ").append(entry.getKey()).append("\n");
            }
        }

        EmbedBuilder panel = new EmbedBuilder()
                .setAuthor("ZDiscord Setup Wizard", null, guildIcon)
                .setTitle("Configure your server integration")
                .setDescription("Select a module from the dropdown below to configure it.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setThumbnail(guildIcon)
                .addField("Connection", "Online - " + online + "/" + max + " players", true)
                .addField("Progress", configured + "/" + MODULES.size() + " modules", true)
                .addField("Module status", statusLines.toString(), false)
                .setFooter("ZDiscord v" + plugin.getDescription().getVersion())
                .setTimestamp(Instant.now());

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(MODULE_MENU_ID)
                .setPlaceholder("Select a module to configure")
                .setMinValues(1)
                .setMaxValues(1);
        for (Map.Entry<String, ModuleInfo> entry : MODULES.entrySet()) {
            ModuleInfo info = entry.getValue();
            String val = plugin.getConfigManager().getString(info.configPath, "");
            String status = isSet(val) ? "Configured" : "Not set";
            menuBuilder.addOption(entry.getKey(),
                    entry.getKey(),
                    status + " - " + info.description);
        }

        event.replyEmbeds(panel.build())
                .addActionRow(menuBuilder.build())
                .setEphemeral(false)
                .queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!MODULE_MENU_ID.equals(event.getComponentId())) {
            return;
        }
        if (event.getMember() == null
                || !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("Only administrators can configure modules.")
                    .setEphemeral(true).queue();
            return;
        }

        String module = event.getValues().get(0);
        ModuleInfo info = MODULES.get(module);
        if (info == null) {
            event.reply("Unknown module.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder prompt = new EmbedBuilder()
                .setTitle("Configure " + capitalize(module))
                .setDescription(info.description + "\n\nSelect a channel to link this module to.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setFooter("Step 1/2 - Select a channel")
                .setTimestamp(Instant.now());

        EntitySelectMenu channelMenu = EntitySelectMenu.create(
                CHANNEL_MENU_PREFIX + module, EntitySelectMenu.SelectTarget.CHANNEL)
                .setPlaceholder("Select a channel")
                .setMinValues(1)
                .setMaxValues(1)
                .build();

        event.replyEmbeds(prompt.build())
                .addActionRow(channelMenu)
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onEntitySelectInteraction(EntitySelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (componentId.startsWith(CHANNEL_MENU_PREFIX)) {
            handleChannelSelect(event);
        } else if (SUPPORT_ROLE_MENU_ID.equals(componentId)) {
            handleSupportRoleSelect(event);
        }
    }

    private void handleChannelSelect(EntitySelectInteractionEvent event) {
        String module = event.getComponentId().replace(CHANNEL_MENU_PREFIX, "");
        ModuleInfo info = MODULES.get(module);
        if (info == null) {
            return;
        }
        var selectedChannel = event.getMentions().getChannels().get(0);
        if (!(selectedChannel instanceof TextChannel)) {
            event.reply("Please select a **text channel**.").setEphemeral(true).queue();
            return;
        }
        TextChannel channel = (TextChannel) selectedChannel;

        saveToConfig(info.configPath, channel.getId());

        switch (module) {
            case "status":
                handleStatusSetup(event, channel);
                return;
            case "tickets":
                handleTicketSetup(event, channel);
                return;
            default:
                postActivationNotice(channel, info, event.getUser().getName());
                event.replyEmbeds(EmbedUtil.success(
                        capitalize(module) + " has been linked to " + channel.getAsMention() + ".")
                        .build())
                        .setEphemeral(true).queue();
                break;
        }
    }

    private void handleStatusSetup(EntitySelectInteractionEvent event, TextChannel channel) {
        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                plugin.getBotManager()::getGuild,
                plugin.getConfigManager().getString("status.embed.title", "Server Status"),
                plugin.getConfigManager().getString("status.embed.color", "#5865F2"),
                plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com"),
                plugin.getConfigManager().getInt("status.update-interval", 30),
                plugin.getConfigManager().getBoolean("status.embed.show-players", true),
                plugin.getConfigManager().getBoolean("status.embed.show-tps", true),
                plugin.getConfigManager().getBoolean("status.embed.show-memory", true),
                plugin.getConfigManager().getDouble("performance.tps-warning", 18.0),
                plugin.getConfigManager().getDouble("performance.tps-critical", 15.0));

        channel.sendMessageEmbeds(StatusEmbedBuilder.build(ctx)).queue(
                msg -> {
                    saveToConfig("channels.status-message", msg.getId());
                    int interval = plugin.getConfigManager().getInt("status.update-interval", 30);
                    event.replyEmbeds(EmbedUtil.success(
                            "Status panel posted in " + channel.getAsMention()
                                    + ". It will auto-update every " + interval + " seconds.")
                            .build())
                            .setEphemeral(true).queue();
                },
                err -> event.replyEmbeds(EmbedUtil.error(
                        "Failed to send status panel: " + err.getMessage()).build())
                        .setEphemeral(true).queue());
    }

    private void handleTicketSetup(EntitySelectInteractionEvent event, TextChannel channel) {
        if (channel.getParentCategory() != null) {
            saveToConfig("channels.ticket-category", channel.getParentCategory().getId());
        }

        EmbedBuilder rolePrompt = new EmbedBuilder()
                .setTitle("Ticket Setup - Step 2")
                .setDescription("Tickets will be created in **"
                        + (channel.getParentCategory() != null
                                ? channel.getParentCategory().getAsMention()
                                : "(no category)") + "**.\n\n"
                        + "Select the support role that should have access to tickets.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setFooter("Step 2/3 - Select support role")
                .setTimestamp(Instant.now());

        EntitySelectMenu roleMenu = EntitySelectMenu.create(
                SUPPORT_ROLE_MENU_ID, EntitySelectMenu.SelectTarget.ROLE)
                .setPlaceholder("Select the support role")
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
        saveToConfig("tickets.support-roles", Collections.singletonList(role.getId()));

        TextChannel panelChannel = null;
        String ticketCategoryId = plugin.getConfigManager().getString("channels.ticket-category", "");
        Guild guild = event.getGuild();
        if (guild != null && !ticketCategoryId.isEmpty()) {
            var category = guild.getCategoryById(ticketCategoryId);
            if (category != null && !category.getTextChannels().isEmpty()) {
                panelChannel = category.getTextChannels().get(0);
            }
        }
        if (panelChannel == null) {
            panelChannel = event.getChannel().asTextChannel();
        }

        postTicketPanel(panelChannel);

        event.replyEmbeds(EmbedUtil.success(
                "Support role: " + role.getAsMention() + "\n"
                        + "Ticket panel: " + panelChannel.getAsMention() + "\n\n"
                        + "Users can now click the button to create support tickets.")
                .build())
                .setEphemeral(true).queue();
    }

    private void postTicketPanel(TextChannel channel) {
        EmbedBuilder ticket = new EmbedBuilder()
                .setTitle("Support Center")
                .setDescription(
                        "Need help? Open a private support ticket by clicking the button below.\n\n"
                                + "A private channel will be created for you and our support team "
                                + "will be notified. Only you and staff can see the channel.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setFooter("ZDiscord Ticket System")
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(ticket.build())
                .addActionRow(Button.success(PANEL_BUTTON_ID, "Create Ticket"))
                .queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!PANEL_BUTTON_ID.equals(event.getComponentId())) {
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }
        plugin.getTicketModule().createTicket(event.getUser(), "General Support", null);
        event.reply("Your support ticket has been created. Check the new channel.")
                .setEphemeral(true).queue();
    }

    private void quickSetup(SlashCommandInteractionEvent event, String module, TextChannel target) {
        ModuleInfo info = MODULES.get(module);
        if (info == null) {
            event.reply("Unknown module: `" + module + "`. Available: "
                    + String.join(", ", MODULE_NAMES)).setEphemeral(true).queue();
            return;
        }

        saveToConfig(info.configPath, target.getId());

        if ("tickets".equals(module) && target.getParentCategory() != null) {
            saveToConfig("channels.ticket-category", target.getParentCategory().getId());
            postTicketPanel(target);
        }

        postActivationNotice(target, info, event.getUser().getName());

        event.replyEmbeds(EmbedUtil.success(
                capitalize(module) + " linked to " + target.getAsMention() + ".")
                .build())
                .setEphemeral(true).queue();
    }

    private void postActivationNotice(TextChannel channel, ModuleInfo info, String username) {
        channel.sendMessageEmbeds(EmbedUtil.simple(
                info.label + " activated",
                "This channel is now linked to ZDiscord for " + info.description,
                ColorUtil.parseHex("#5865F2"))
                .setFooter("Configured by " + username)
                .build())
                .queue(s -> { }, err -> plugin.debug("Activation notice failed: " + err.getMessage()));
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

    private boolean isSet(String value) {
        return value != null && !value.isEmpty() && !value.startsWith("YOUR_");
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static final class ModuleInfo {
        final String label;
        final String configPath;
        final String description;

        ModuleInfo(String label, String configPath, String description) {
            this.label = label;
            this.configPath = configPath;
            this.description = description;
        }
    }
}

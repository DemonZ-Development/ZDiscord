package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.EmbedUtil;
import dev.demonz.zdiscord.util.StatusEmbedBuilder;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.EntitySelectMenu;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.interactions.modals.Modal;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import net.dv8tion.jda.api.interactions.components.text.TextInput;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


public class SetupCommand extends ListenerAdapter {

    private final ZDiscord plugin;

    private static final String MODULE_MENU_ID = "zdiscord_setup_module";
    private static final String SUPPORT_ROLE_MENU_ID = "zdiscord_setup_support_role";
    private static final String CHANNEL_MENU_PREFIX = "zdiscord_setup_channel:";


    private static final String TICKET_CATEGORY_MENU_ID = "zdiscord_setup_ticket_cat_menu";
    private static final String TICKET_CATEGORY_PREFIX = "zdiscord_setup_ticket_cat:";
    private static final String TICKET_CATEGORY_MODAL_PREFIX = "zdiscord_setup_ticket_modal:";

    private static final String BTN_ADD = "zdiscord_setup_ticket_add";
    private static final String BTN_EDIT = "zdiscord_setup_ticket_edit";
    private static final String BTN_REMOVE = "zdiscord_setup_ticket_remove";
    private static final String BTN_UP = "zdiscord_setup_ticket_up";
    private static final String BTN_DOWN = "zdiscord_setup_ticket_down";
    private static final String BTN_DONE = "zdiscord_setup_ticket_done";
    private static final String BTN_CONFIRM_REMOVE = "zdiscord_setup_ticket_confirm_remove:";

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
        if (!isAdmin(event.getMember())) {
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
        boolean botReady = plugin.getBotManager() != null && plugin.getBotManager().isConnected();

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
                statusLines.append(":white_check_mark: **").append(entry.getKey())
                        .append("** -> <#").append(val).append(">\n");
            } else {
                statusLines.append(":black_square_for_button: ").append(entry.getKey()).append("\n");
            }
        }

        int catCount = getCategoriesFromConfig().size();
        String catLine = catCount == 0
                ? ":black_square_for_button: ticket categories (none yet)"
                : ":white_check_mark: " + catCount + " ticket categor"
                        + (catCount == 1 ? "y" : "ies") + " configured";

        EmbedBuilder panel = new EmbedBuilder()
                .setAuthor("ZDiscord Setup Wizard", null, guildIcon)
                .setTitle(":gear: Configure your server integration")
                .setDescription("Pick a module from the dropdown below to configure it.\n"
                        + "Each module has a guided flow with sensible defaults.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setThumbnail(guildIcon)
                .addField(":satellite: Connection",
                        (botReady ? ":white_check_mark: Bot online" : ":x: Bot not connected")
                                + "\n" + online + "/" + max + " players", true)
                .addField(":bar_chart: Progress",
                        configured + "/" + MODULES.size() + " modules\n" + catLine, true)
                .addField(":clipboard: Module status", statusLines.toString(), false)
                .setFooter("ZDiscord v" + plugin.getDescription().getVersion()
                        + "  \u2022  /setup for this wizard")
                .setTimestamp(Instant.now());

        StringSelectMenu.Builder menuBuilder = StringSelectMenu.create(MODULE_MENU_ID)
                .setPlaceholder("Select a module to configure")
                .setMinValues(1)
                .setMaxValues(1);
        for (Map.Entry<String, ModuleInfo> entry : MODULES.entrySet()) {
            ModuleInfo info = entry.getValue();
            String val = plugin.getConfigManager().getString(info.configPath, "");
            String status = isSet(val) ? "Configured" : "Not set";
            String desc = status + "  \u2022  " + info.description;
            if (desc.length() > 100) {
                desc = desc.substring(0, 97) + "...";
            }
            menuBuilder.addOption(entry.getKey(), entry.getKey(), desc);
        }

        event.replyEmbeds(panel.build())
                .addActionRow(menuBuilder.build())
                .setEphemeral(false)
                .queue();
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        String componentId = event.getComponentId();
        if (MODULE_MENU_ID.equals(componentId)) {
            handleModuleSelect(event);
        } else if (componentId.equals(TICKET_CATEGORY_MENU_ID)) {
            handleTicketCategorySelect(event);
        }
    }

    private void handleModuleSelect(StringSelectInteractionEvent event) {
        if (!isAdmin(event.getMember())) {
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
                .setTitle(":gear: Configure " + capitalize(module))
                .setDescription(info.description
                        + "\n\nSelect a channel to link this module to. The dropdown only shows channels the bot can see.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setFooter("Step 1/" + ("tickets".equals(module) ? "3" : "1")
                        + "  \u2022  Select a channel")
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
        saveToConfig("tickets.panel-channel", channel.getId());
        if (channel.getParentCategory() != null) {
            saveToConfig("channels.ticket-category", channel.getParentCategory().getId());
        }

        EmbedBuilder rolePrompt = new EmbedBuilder()
                .setTitle(":ticket: Ticket Setup  \u2014  Step 2 of 3")
                .setDescription("Tickets will be created in **"
                        + (channel.getParentCategory() != null
                                ? channel.getParentCategory().getAsMention()
                                : "(no category)") + "**.\n\n"
                        + "Pick the support role that should have access to every ticket. "
                        + "You can add more support roles in `config.yml` later "
                        + "(`tickets.support-roles` is a list).")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setFooter("Step 2/3  \u2022  Select support role")
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


        showTicketCategoryManager(event, role.getAsMention());
    }




    private void showTicketCategoryManager(net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent event, String supportRoleMention) {
        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(":ticket: Ticket Categories  \u2014  Step 3 of 3")
                .setDescription(buildCategoryListText(cats)
                        + "\n\nUse the buttons below to manage your categories. "
                        + "At least one category is required to post a panel.")
                .setColor(ColorUtil.parseHex("#5865F2"))
                .setFooter("Step 3/3  \u2022  Configure categories  \u2022  Support role: "
                        + supportRoleMention)
                .setTimestamp(Instant.now());

        List<net.dv8tion.jda.api.interactions.components.LayoutComponent> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                Button.success(BTN_ADD, ":heavy_plus_sign: Add"),
                Button.primary(BTN_EDIT, ":pencil2: Edit"),
                Button.danger(BTN_REMOVE, ":wastebasket: Remove"),
                Button.secondary(BTN_UP, ":arrow_up_small: Move up"),
                Button.secondary(BTN_DOWN, ":arrow_down_small: Move down")));

        if (!cats.isEmpty()) {

            StringSelectMenu.Builder select = StringSelectMenu.create(TICKET_CATEGORY_MENU_ID)
                    .setPlaceholder("Pick a category for the action")
                    .setMinValues(1)
                    .setMaxValues(1);
            for (Map.Entry<String, CategoryDraft> entry : cats.entrySet()) {
                CategoryDraft c = entry.getValue();
                String desc = c.description == null || c.description.isEmpty()
                        ? "(no description)"
                        : c.description;
                if (desc.length() > 100) {
                    desc = desc.substring(0, 97) + "...";
                }
                String label = (c.emoji == null || c.emoji.isEmpty() ? "" : c.emoji + " ") + c.label;
                if (label.length() > 100) {
                    label = label.substring(0, 97) + "...";
                }
                select.addOption(label, entry.getKey(), desc);
            }
            rows.add(ActionRow.of(select.build()));
        }

        rows.add(ActionRow.of(Button.success(BTN_DONE, ":white_check_mark: Done & post panel")));

        if (event instanceof SlashCommandInteractionEvent) {
            ((SlashCommandInteractionEvent) event).replyEmbeds(embed.build())
                    .addComponents(rows)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof ButtonInteractionEvent) {
            ((ButtonInteractionEvent) event).replyEmbeds(embed.build())
                    .addComponents(rows)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof StringSelectInteractionEvent) {
            ((StringSelectInteractionEvent) event).editMessageEmbeds(embed.build())
                    .setComponents(rows)
                    .queue();
        } else if (event instanceof EntitySelectInteractionEvent) {
            ((EntitySelectInteractionEvent) event).replyEmbeds(embed.build())
                    .addComponents(rows)
                    .setEphemeral(true)
                    .queue();
        } else if (event instanceof ModalInteractionEvent) {

            ((ModalInteractionEvent) event).getHook()
                    .editOriginalEmbeds(embed.build())
                    .setComponents(rows)
                    .queue();
        }
    }

    private void handleTicketCategorySelect(StringSelectInteractionEvent event) {





        String picked = event.getValues().get(0);

        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        CategoryDraft pickedCat = cats.get(picked);
        if (pickedCat == null) {
            event.reply("That category no longer exists.").setEphemeral(true).queue();
            return;
        }
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(":ticket: Ticket Categories  \u2014  Step 3 of 3")
                .setDescription(buildCategoryListText(cats)
                        + "\n\n:point_right: **Selected: **" + pickedCat.emoji + " " + pickedCat.label
                        + " (`" + picked + "`)\n"
                        + "Now press an action button: **Edit**, **Remove**, "
                        + "**Move up**, or **Move down**.")
                .setColor(ColorUtil.parseHex("#F39C12"))
                .setFooter("Selection pending  \u2022  category id: " + picked)
                .setTimestamp(Instant.now());

        List<net.dv8tion.jda.api.interactions.components.LayoutComponent> rows = new ArrayList<>();
        rows.add(ActionRow.of(
                Button.success(BTN_ADD, ":heavy_plus_sign: Add"),
                Button.primary(BTN_EDIT, ":pencil2: Edit"),
                Button.danger(BTN_REMOVE, ":wastebasket: Remove"),
                Button.secondary(BTN_UP, ":arrow_up_small: Move up"),
                Button.secondary(BTN_DOWN, ":arrow_down_small: Move down")));

        StringSelectMenu.Builder select = StringSelectMenu.create(TICKET_CATEGORY_MENU_ID)
                .setPlaceholder("Pick a category for the action")
                .setMinValues(1)
                .setMaxValues(1);
        for (Map.Entry<String, CategoryDraft> entry : cats.entrySet()) {
            CategoryDraft c = entry.getValue();
            String desc = c.description == null || c.description.isEmpty()
                    ? "(no description)"
                    : c.description;
            if (desc.length() > 100) {
                desc = desc.substring(0, 97) + "...";
            }
            String label = (c.emoji == null || c.emoji.isEmpty() ? "" : c.emoji + " ") + c.label;
            if (label.length() > 100) {
                label = label.substring(0, 97) + "...";
            }
            select.addOption(label, entry.getKey(), desc);
        }
        rows.add(ActionRow.of(select.build()));
        rows.add(ActionRow.of(Button.success(BTN_DONE, ":white_check_mark: Done & post panel")));

        event.editMessageEmbeds(embed.build()).setComponents(rows).queue();
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        if (id.startsWith("zdiscord_create_ticket")) {
            return;
        }
        if (!isAdmin(event.getMember())) {
            event.reply("Only administrators can manage categories.")
                    .setEphemeral(true).queue();
            return;
        }

        switch (id) {
            case BTN_ADD:
                showCategoryModal(event, null);
                return;
            case BTN_EDIT: {
                String picked = lastSelectedCategory(event.getMessage().getEmbeds());
                if (picked == null) {
                    event.reply("Pick a category from the dropdown first, then press **Edit**.")
                            .setEphemeral(true).queue();
                    return;
                }
                showCategoryModal(event, picked);
                return;
            }
            case BTN_REMOVE: {
                String picked = lastSelectedCategory(event.getMessage().getEmbeds());
                if (picked == null) {
                    event.reply("Pick a category from the dropdown first, then press **Remove**.")
                            .setEphemeral(true).queue();
                    return;
                }
                askRemoveConfirm(event, picked);
                return;
            }
            case BTN_UP:
            case BTN_DOWN: {
                String picked = lastSelectedCategory(event.getMessage().getEmbeds());
                if (picked == null) {
                    event.reply("Pick a category from the dropdown first, then press the move button.")
                            .setEphemeral(true).queue();
                    return;
                }
                reorderCategory(event, picked, id.equals(BTN_UP) ? -1 : +1);
                return;
            }
            case BTN_DONE:
                finishTicketSetup(event);
                return;
            default:
                if (id.startsWith(BTN_CONFIRM_REMOVE)) {
                    String picked = id.substring(BTN_CONFIRM_REMOVE.length());
                    removeCategory(event, picked);
                }
        }
    }


    private String lastSelectedCategory(List<net.dv8tion.jda.api.entities.MessageEmbed> embeds) {
        if (embeds.isEmpty()) {
            return null;
        }
        var footer = embeds.get(0).getFooter();
        if (footer == null || footer.getText() == null) {
            return null;
        }
        String prefix = "category id: ";
        String text = footer.getText();
        int idx = text.indexOf(prefix);
        if (idx < 0) {
            return null;
        }
        return text.substring(idx + prefix.length()).trim();
    }

    private void showCategoryModal(ButtonInteractionEvent event, String existingId) {
        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        CategoryDraft existing = existingId != null ? cats.get(existingId) : null;

        String title = existing == null
                ? "Add a ticket category"
                : "Edit category  \u2014  " + existing.label;
        String modalId = TICKET_CATEGORY_MODAL_PREFIX + (existingId == null ? "_new" : existingId);

        Modal.Builder modal = Modal.create(modalId, title);
        modal.addActionRow(TextInput.create("id", "ID (lowercase, no spaces)",
                TextInputStyle.SHORT)
                .setValue(existingId != null ? existingId : "")
                .setPlaceholder("e.g. general, bug, billing")
                .setRequired(true)
                .setMaxLength(32)
                .build());
        modal.addActionRow(TextInput.create("label", "Label",
                TextInputStyle.SHORT)
                .setValue(existing == null ? "" : existing.label)
                .setPlaceholder("e.g. General Support")
                .setRequired(true)
                .setMaxLength(80)
                .build());
        modal.addActionRow(TextInput.create("description", "Description",
                TextInputStyle.PARAGRAPH)
                .setValue(existing == null || existing.description == null ? "" : existing.description)
                .setPlaceholder("One sentence describing when to use this category.")
                .setRequired(false)
                .setMaxLength(400)
                .build());
        modal.addActionRow(TextInput.create("emoji", "Emoji (optional)",
                TextInputStyle.SHORT)
                .setValue(existing == null || existing.emoji == null ? "" : existing.emoji)
                .setPlaceholder("â“  âš¡  ðŸ›  (single emoji)")
                .setRequired(false)
                .setMaxLength(8)
                .build());
        modal.addActionRow(TextInput.create("color", "Color (hex, optional)",
                TextInputStyle.SHORT)
                .setValue(existing == null || existing.color == null ? "" : existing.color)
                .setPlaceholder("#5865F2")
                .setRequired(false)
                .setMaxLength(9)
                .build());

        event.replyModal(modal.build()).queue();
    }

    @Override
    public void onModalInteraction(ModalInteractionEvent event) {
        String id = event.getModalId();
        if (!id.startsWith(TICKET_CATEGORY_MODAL_PREFIX)) {
            return;
        }
        String existingId = id.substring(TICKET_CATEGORY_MODAL_PREFIX.length());
        if ("_new".equals(existingId)) {
            existingId = null;
        }

        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        String newId = event.getValue("id").getAsString().trim().toLowerCase();
        String label = event.getValue("label").getAsString().trim();
        String description = optionalValue(event, "description");
        String emoji = optionalValue(event, "emoji").trim();
        String color = optionalValue(event, "color").trim();
        if (color.isEmpty()) {
            color = "#5865F2";
        }
        if (!color.startsWith("#")) {
            color = "#" + color;
        }
        if (newId.isEmpty() || label.isEmpty()) {
            event.reply("Category ID and Label are required.")
                    .setEphemeral(true).queue();
            return;
        }
        if (existingId == null && cats.containsKey(newId)) {
            event.reply("A category with that ID already exists. Pick a different ID.")
                    .setEphemeral(true).queue();
            return;
        }
        if (existingId != null && !existingId.equals(newId) && cats.containsKey(newId)) {
            event.reply("A category with that ID already exists. Pick a different ID.")
                    .setEphemeral(true).queue();
            return;
        }

        if (existingId != null) {
            cats.remove(existingId);
        }
        cats.put(newId, new CategoryDraft(newId, label, description, emoji, color));
        saveCategoriesToConfig(cats);


        showTicketCategoryManager(event, supportRoleMention(event));
    }


    private String supportRoleMention(net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent event) {
        String roleId = null;
        var list = plugin.getConfigManager().getStringList("tickets.support-roles");
        if (!list.isEmpty()) {
            roleId = list.get(0);
        }
        if (roleId == null) {
            return "(unset)";
        }
        Guild guild = event.getGuild();
        if (guild == null) {
            return "<@&" + roleId + ">";
        }
        Role role = guild.getRoleById(roleId);
        return role != null ? role.getAsMention() : "<@&" + roleId + ">";
    }

    private void askRemoveConfirm(ButtonInteractionEvent event, String categoryId) {
        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        CategoryDraft c = cats.get(categoryId);
        if (c == null) {
            event.reply("That category no longer exists.").setEphemeral(true).queue();
            return;
        }
        event.replyEmbeds(EmbedUtil.error(
                "Are you sure you want to remove **" + c.label
                        + "** (`" + categoryId + "`)?\n"
                        + "Existing open tickets in this category will not be deleted; "
                        + "users just won't be able to open new ones in it.")
                .build())
                .addActionRow(
                        Button.danger(BTN_CONFIRM_REMOVE + categoryId, ":wastebasket: Yes, remove"),
                        Button.secondary(BTN_DONE, ":x: Cancel"))
                .setEphemeral(true)
                .queue();
    }

    private void removeCategory(ButtonInteractionEvent event, String categoryId) {
        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        cats.remove(categoryId);
        saveCategoriesToConfig(cats);

        showTicketCategoryManager(event, supportRoleMention(event));
    }

    private void reorderCategory(ButtonInteractionEvent event, String categoryId, int delta) {
        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        List<String> ids = new ArrayList<>(cats.keySet());
        int idx = ids.indexOf(categoryId);
        int newIdx = idx + delta;
        if (idx < 0 || newIdx < 0 || newIdx >= ids.size()) {
            event.reply("Can't move that category further in that direction.")
                    .setEphemeral(true).queue();
            return;
        }
        java.util.Collections.swap(ids, idx, newIdx);
        Map<String, CategoryDraft> reordered = new LinkedHashMap<>();
        for (String id : ids) {
            reordered.put(id, cats.get(id));
        }
        saveCategoriesToConfig(reordered);

        showTicketCategoryManager(event, supportRoleMention(event));
    }

    private void finishTicketSetup(ButtonInteractionEvent event) {
        Map<String, CategoryDraft> cats = getCategoriesFromConfig();
        if (cats.isEmpty()) {
            event.reply("Add at least one ticket category before posting the panel.")
                    .setEphemeral(true).queue();
            return;
        }
        TextChannel panelChannel = null;
        String ticketCategoryId = plugin.getConfigManager().getString("channels.ticket-category", "");
        Guild guild = event.getGuild();
        if (guild != null && !ticketCategoryId.isEmpty()) {
            var category = guild.getCategoryById(ticketCategoryId);
            if (category != null && !category.getTextChannels().isEmpty()) {
                panelChannel = category.getTextChannels().get(0);
            }
        }
        if (panelChannel == null && event.getChannel() instanceof TextChannel) {
            panelChannel = (TextChannel) event.getChannel();
        }
        if (panelChannel == null) {
            event.reply("Could not find a channel to post the panel in.")
                    .setEphemeral(true).queue();
            return;
        }

        postTicketPanel(panelChannel);
        event.replyEmbeds(EmbedUtil.success(
                ":ticket: Ticket panel posted in " + panelChannel.getAsMention() + ".\n"
                        + ":white_check_mark: Categories: " + cats.size() + "\n"
                        + "Users can now click the dropdown to open a support ticket.")
                .build())
                .setEphemeral(true)
                .queue();
    }

    private void postTicketPanel(TextChannel channel) {
        if (plugin.getTicketModule() == null) {
            plugin.getLogger().warning("Ticket module is not enabled; skipping panel post.");
            return;
        }
        plugin.getTicketModule().postPanel(channel);
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
        plugin.getConfigManager().getConfig().set(path, value);
        plugin.getConfigManager().save();
    }

    private void saveToConfig(String path, List<String> values) {
        plugin.getConfigManager().getConfig().set(path, values);
        plugin.getConfigManager().save();
    }

    private Map<String, CategoryDraft> getCategoriesFromConfig() {
        var config = plugin.getConfigManager().getConfig();
        ConfigurationSection sec = config.getConfigurationSection("tickets.categories");
        Map<String, CategoryDraft> out = new LinkedHashMap<>();
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) continue;
                out.put(id, new CategoryDraft(
                        id,
                        c.getString("label", id),
                        c.getString("description", ""),
                        c.getString("emoji", ""),
                        c.getString("color", "#5865F2")));
            }
            return out;
        }

        List<Map<?, ?>> rawList = config.getMapList("tickets.categories");
        for (Map<?, ?> entry : rawList) {
            Object idObj = entry.get("id");
            if (idObj == null) continue;
            String id = idObj.toString();
            Object label = entry.get("label");
            Object description = entry.get("description");
            Object emoji = entry.get("emoji");
            Object color = entry.get("color");
            out.put(id, new CategoryDraft(
                    id,
                    label != null ? label.toString() : id,
                    description != null ? description.toString() : "",
                    emoji != null ? emoji.toString() : "",
                    color != null ? color.toString() : "#5865F2"));
        }
        return out;
    }

    private void saveCategoriesToConfig(Map<String, CategoryDraft> cats) {
        var cfg = plugin.getConfigManager().getConfig();
        cfg.set("tickets.categories", null);
        for (Map.Entry<String, CategoryDraft> entry : cats.entrySet()) {
            CategoryDraft c = entry.getValue();
            String base = "tickets.categories." + entry.getKey();
            cfg.set(base + ".label", c.label);
            cfg.set(base + ".description", c.description);
            cfg.set(base + ".emoji", c.emoji);
            cfg.set(base + ".color", c.color);
        }
        plugin.getConfigManager().save();
    }

    private String buildCategoryListText(Map<String, CategoryDraft> cats) {
        if (cats.isEmpty()) {
            return ":warning: No categories yet. Press **Add** to create one.";
        }
        StringBuilder sb = new StringBuilder();
        int i = 1;
        for (Map.Entry<String, CategoryDraft> entry : cats.entrySet()) {
            CategoryDraft c = entry.getValue();
            sb.append("`").append(i++).append(".` ");
            if (c.emoji != null && !c.emoji.isEmpty()) {
                sb.append(c.emoji).append(" ");
            }
            sb.append("**").append(c.label).append("**")
                    .append("  \u2014  `").append(entry.getKey()).append("`\n");
            if (c.description != null && !c.description.isEmpty()) {
                sb.append("> ").append(c.description).append("\n");
            }
        }
        return sb.toString();
    }

    private String optionalValue(ModalInteractionEvent event, String key) {
        ModalMapping m = event.getValue(key);
        return m == null ? "" : m.getAsString();
    }

    private boolean isSet(String value) {
        return value != null && !value.isEmpty() && !value.startsWith("YOUR_");
    }

    private boolean isAdmin(net.dv8tion.jda.api.entities.Member member) {
        return member != null && member.hasPermission(Permission.ADMINISTRATOR);
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


    private static final class CategoryDraft {
        final String id;
        final String label;
        final String description;
        final String emoji;
        final String color;

        CategoryDraft(String id, String label, String description, String emoji, String color) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.emoji = emoji == null ? "" : emoji;
            this.color = color == null || color.isEmpty() ? "#5865F2" : color;
        }
    }
}

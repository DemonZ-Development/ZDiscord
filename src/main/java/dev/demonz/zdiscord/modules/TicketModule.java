package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class TicketModule {

    public static final String PANEL_BUTTON_ID = "zdiscord_create_ticket";
    public static final String PANEL_SELECT_ID = "zdiscord_ticket_category";
    public static final String OWNER_TOPIC_PREFIX = "zdticket:";
    private static final String SNOWFLAKE_PATTERN = "\\d{17,20}";

    private final ZDiscord plugin;
    private final Map<String, Integer> openTicketsByUser = new ConcurrentHashMap<>();
    private final AtomicInteger ticketCounter = new AtomicInteger(0);

    public TicketModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        ticketCounter.set(plugin.getStorageManager().getDataInt("ticket-counter", 0));
        openTicketsByUser.clear();
        String openData = plugin.getStorageManager().getData("open-tickets", "");
        if (!openData.isEmpty()) {
            for (String entry : openData.split(";")) {
                int eq = entry.indexOf('=');
                if (eq <= 0 || eq == entry.length() - 1) {
                    continue;
                }
                String key = entry.substring(0, eq);
                String value = entry.substring(eq + 1);
                try {
                    openTicketsByUser.put(key, Integer.parseInt(value));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        plugin.debug("Ticket module initialised (counter: " + ticketCounter.get()
                + ", categories: " + getCategories().size()
                + ", tracked users: " + openTicketsByUser.size() + ")");
    }

    public void reload() {
    }

    public void createTicketFromMC(Player player, String subject) {
        if (plugin.getLinkModule() == null) {
            player.sendMessage(plugin.getMessageManager().get("link-required"));
            return;
        }
        String discordId = plugin.getLinkModule().getDiscordId(player.getUniqueId());
        String categoryId = defaultCategoryId();

        if (!canCreate(player.getUniqueId().toString(), discordId)) {
            int max = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
            player.sendMessage(plugin.getMessageManager().get(
                    "ticket-max-reached", "%max%", String.valueOf(max)));
            return;
        }

        plugin.getPlatformAdapter().runAsync(() -> {
            TicketCategory cat = getCategory(categoryId);
            String effectiveSubject = subject != null && !subject.isBlank()
                    ? subject
                    : (cat != null ? cat.label : "Support");
            TextChannel channel = createTicketChannel(
                    player.getName(), effectiveSubject, categoryId,
                    discordId, player.getUniqueId().toString());
            if (channel != null) {
                markOpened(player.getUniqueId().toString());
                if (discordId != null) {
                    markOpened(discordId);
                }
                plugin.getPlatformAdapter().runForEntity(player, () -> player.sendMessage(
                        plugin.getMessageManager().get("ticket-created",
                                "%channel%", channel.getName())));
            }
        });
    }

    public void createTicket(User user, String subject, SlashCommandInteractionEvent event) {
        createTicket(user, subject, defaultCategoryId(), event);
    }

    public void createTicket(User user, String subject, String categoryId,
                             SlashCommandInteractionEvent event) {
        String message = createTicketMessage(user, subject, categoryId);
        if (event != null) {
            event.reply(message).setEphemeral(true).queue();
        }
    }

    private String createTicketMessage(User user, String subject, String categoryId) {
        if (!canCreate(user.getId(), user.getId())) {
            int max = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
            return "You have reached the maximum number of open tickets (" + max + ").";
        }

        TicketCategory cat = getCategory(categoryId);
        String finalCategory = cat != null ? cat.id : defaultCategoryId();
        if (finalCategory == null) {
            return "No default ticket category is configured.";
        }
        TicketCategory finalCat = getCategory(finalCategory);
        String effectiveSubject = subject != null && !subject.isBlank()
                ? subject
                : (finalCat != null ? finalCat.label : "Support");
        TextChannel channel = createTicketChannel(
                user.getName(), effectiveSubject, finalCategory, user.getId(), user.getId());
        if (channel == null) {
            return "Failed to create ticket. Please contact an admin.";
        }
        markOpened(user.getId());
        plugin.getLogger().info("Ticket #" + ticketCounter.get() + " opened for " + user.getName()
                + " (category " + finalCategory + ", channel " + channel.getId() + ")");
        return "Ticket created. See " + channel.getAsMention();
    }

    public String createTicketForCategory(User user, String categoryId) {
        if (!canCreate(user.getId(), user.getId())) {
            int max = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
            return "You have reached the maximum number of open tickets (" + max + ").";
        }
        TicketCategory cat = getCategory(categoryId);
        if (cat == null) {
            return "That ticket category is no longer available.";
        }
        TextChannel channel = createTicketChannel(
                user.getName(), cat.label, cat.id, user.getId(), user.getId());
        if (channel == null) {
            return "Failed to create ticket. Please contact an admin.";
        }
        markOpened(user.getId());
        return "Ticket created. See " + channel.getAsMention();
    }

    public static final class TicketCategory {
        public final String id;
        public final String label;
        public final String description;
        public final String emoji;
        public final String colorHex;

        public TicketCategory(String id, String label, String description,
                              String emoji, String colorHex) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.emoji = emoji != null ? emoji : "";
            this.colorHex = colorHex != null && !colorHex.isEmpty() ? colorHex : "#5865F2";
        }
    }

    public Map<String, TicketCategory> getCategories() {
        return loadCategories(plugin.getConfigManager().getConfig());
    }

    public static Map<String, TicketCategory> loadCategories(ConfigurationSection root) {
        Map<String, TicketCategory> out = new LinkedHashMap<>();
        if (root == null) {
            return out;
        }
        ConfigurationSection sec = root.getConfigurationSection("tickets.categories");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection c = sec.getConfigurationSection(id);
                if (c == null) {
                    continue;
                }
                String label = c.getString("label", id);
                String description = c.getString("description", "");
                String emoji = c.getString("emoji", "");
                String color = c.getString("color", "#5865F2");
                out.put(id, new TicketCategory(id, label, description, emoji, color));
            }
            return out;
        }

        List<Map<?, ?>> rawList = root.getMapList("tickets.categories");
        for (Map<?, ?> entry : rawList) {
            Object idObj = entry.get("id");
            if (idObj == null) continue;
            String id = idObj.toString();
            Object label = entry.get("label");
            Object description = entry.get("description");
            Object emoji = entry.get("emoji");
            Object color = entry.get("color");
            out.put(id, new TicketCategory(
                    id,
                    label != null ? label.toString() : id,
                    description != null ? description.toString() : "",
                    emoji != null ? emoji.toString() : "",
                    color != null ? color.toString() : "#5865F2"));
        }
        return out;
    }

    public TicketCategory getCategory(String id) {
        if (id == null) {
            return null;
        }
        Map<String, TicketCategory> cats = getCategories();
        TicketCategory direct = cats.get(id);
        if (direct != null) {
            return direct;
        }
        for (TicketCategory category : cats.values()) {
            if (id.equals(category.label) || id.equals(displayLabel(category))) {
                return category;
            }
        }
        return null;
    }

    public String defaultCategoryId() {
        Map<String, TicketCategory> cats = getCategories();
        if (cats.isEmpty()) {
            return null;
        }
        return cats.keySet().iterator().next();
    }

    private int safeParseHex(String hex, int fallback) {
        try {
            return ColorUtil.parseHex(hex).getRGB() & 0xFFFFFF;
        } catch (Exception e) {
            return fallback;
        }
    }

    private boolean canCreate(String mcKey, String discordId) {
        int max = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
        if (openTicketsByUser.getOrDefault(mcKey, 0) >= max) {
            return false;
        }
        if (discordId != null && openTicketsByUser.getOrDefault(discordId, 0) >= max) {
            return false;
        }
        return true;
    }

    private void markOpened(String key) {
        openTicketsByUser.merge(key, 1, Integer::sum);
        saveOpenTickets();
    }

    private TextChannel createTicketChannel(String username, String subject,
                                            String categoryId, String discordId,
                                            String ownerKey) {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            return null;
        }

        Category category = null;
        String categoryChannelId = plugin.getConfigManager().getString("channels.ticket-category");
        if (isUsableSnowflake(categoryChannelId)) {
            category = guild.getCategoryById(categoryChannelId);
        }

        int currentTicket = ticketCounter.incrementAndGet();
        plugin.getStorageManager().setData("ticket-counter", currentTicket);

        TicketCategory cat = getCategory(categoryId);

        String channelName = "ticket-" + String.format(Locale.ROOT, "%04d", currentTicket)
                + "-" + username.toLowerCase().replaceAll("[^a-z0-9-]", "");

        try {
            var builder = guild.createTextChannel(channelName)
                    .setTopic(OWNER_TOPIC_PREFIX + ownerKey);
            if (category != null) {
                builder = builder.setParent(category);
            }

            builder = builder.addPermissionOverride(guild.getPublicRole(),
                    EnumSet.noneOf(Permission.class),
                    EnumSet.of(Permission.VIEW_CHANNEL));

            for (String roleId : plugin.getConfigManager().getStringList("tickets.support-roles")) {
                if (!isUsableSnowflake(roleId)) {
                    continue;
                }
                var role = guild.getRoleById(roleId);
                if (role != null) {
                    builder = builder.addPermissionOverride(role,
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                            EnumSet.noneOf(Permission.class));
                }
            }

            if (discordId != null) {
                Member member = guild.getMemberById(discordId);
                if (member != null) {
                    builder = builder.addMemberPermissionOverride(member.getIdLong(),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                            EnumSet.noneOf(Permission.class));
                }
            }

            TextChannel channel = builder.complete();
            sendTicketWelcome(channel, username, subject, cat);
            return channel;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create ticket channel: " + e.getMessage());
            return null;
        }
    }

    private void sendTicketWelcome(TextChannel channel, String username,
                                   String subject, TicketCategory cat) {
        String color = cat != null ? cat.colorHex
                : plugin.getConfigManager().getString("tickets.panel.color", "#5865F2");
        String categoryLabel = cat != null ? cat.label : "Support";

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor("Support Ticket", null, channel.getGuild().getIconUrl())
                .setTitle("🎫 " + categoryLabel)
                .setDescription(
                        "Hello **" + username + "**, a staff member will be with you shortly.\n"
                                + "Please describe your issue in detail and avoid pinging staff.")
                .addField("Subject", subject != null ? subject : categoryLabel, false)
                .addField("Category", categoryLabel, true)
                .addField("Opened by", "`" + username + "`", true)
                .setColor(safeParseHex(color, 0x5865F2))
                .setFooter("Ticket #" + ticketCounter.get() + " \u2022 Use the buttons below to manage")
                .setTimestamp(Instant.now());

        channel.sendMessageEmbeds(embed.build())
                .setComponents(ActionRow.of(
                        Button.danger(PANEL_BUTTON_ID + ":close", "\u274c Close Ticket"),
                        Button.success(PANEL_BUTTON_ID + ":claim", "\u2705 Claim Ticket"),
                        Button.secondary(PANEL_BUTTON_ID + ":transcript", "\ud83d\udcdd Transcript")))
                .queue();
    }

    public void postPanel(TextChannel channel) {
        if (channel == null) {
            return;
        }

        Map<String, TicketCategory> cats = getCategories();
        if (cats.isEmpty()) {
            plugin.getLogger().warning("No ticket categories configured; skipping panel post.");
            return;
        }

        String title = plugin.getConfigManager().getString(
                "tickets.panel.title", "Support Center");
        String description = plugin.getConfigManager().getString(
                "tickets.panel.description",
                "Need help? Pick a category below to open a private ticket.");
        String colorHex = plugin.getConfigManager().getString(
                "tickets.panel.color", "#5865F2");
        String thumbnail = plugin.getConfigManager().getString(
                "tickets.panel.thumbnail", "");
        String image = plugin.getConfigManager().getString(
                "tickets.panel.image", "");
        String footer = plugin.getConfigManager().getString(
                "tickets.panel.footer", "ZDiscord Ticket System");

        Guild guild = channel.getGuild();
        String iconUrl = guild.getIconUrl() != null ? guild.getIconUrl() + "?size=256" : null;

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(guild.getName(), null, iconUrl)
                .setTitle("🎫 " + title)
                .setDescription(description)
                .setColor(safeParseHex(colorHex, 0x5865F2))
                .addField("👥 Members", String.valueOf(guild.getMemberCount()), true)
                .addField("#️⃣ Channels", String.valueOf(guild.getTextChannels().size()), true)
                .addField("🔐 Privacy",
                        "Tickets are private to you and staff.", true)
                .setFooter(footer)
                .setTimestamp(Instant.now());

        if (thumbnail != null && !thumbnail.isEmpty()) {
            embed.setThumbnail(thumbnail);
        } else if (iconUrl != null) {
            embed.setThumbnail(iconUrl);
        }
        if (image != null && !image.isEmpty()) {
            embed.setImage(image);
        }

        StringBuilder categoryList = new StringBuilder();
        for (TicketCategory c : cats.values()) {
            categoryList.append(c.emoji).append(" **")
                    .append(c.label).append("** \u2014 ")
                    .append(c.description).append("\n");
        }
        embed.addField("✨ Categories", categoryList.toString(), false);

        StringSelectMenu.Builder menu = StringSelectMenu.create(PANEL_SELECT_ID)
                .setPlaceholder("Select a ticket category")
                .setMinValues(1)
                .setMaxValues(1);
        for (TicketCategory c : cats.values()) {
            String label = displayLabel(c);
            String desc = c.description != null && c.description.length() > 100
                    ? c.description.substring(0, 97) + "..."
                    : (c.description == null ? "" : c.description);
            menu.addOption(label, c.id, desc);
        }

        List<ActionRow> rows = new ArrayList<>();
        rows.add(ActionRow.of(menu.build()));
        rows.add(ActionRow.of(Button.primary(PANEL_BUTTON_ID + ":quick", "\u26a1 Quick Open")));

        channel.sendMessageEmbeds(embed.build()).setComponents(rows).queue();
    }

    /**
     * Decrements the open-ticket counter for whoever owned the ticket
     * (a Discord id or a Minecraft uuid) plus their linked counterpart.
     */
    public void onTicketClose(String ownerKey) {
        decrementCount(ownerKey);
        if (plugin.getLinkModule() != null) {
            if (isUuid(ownerKey)) {
                String discordId = plugin.getLinkModule()
                        .getDiscordId(UUID.fromString(ownerKey));
                if (discordId != null) {
                    decrementCount(discordId);
                }
            } else {
                UUID mcId = plugin.getLinkModule().getPlayerUUID(ownerKey);
                if (mcId != null) {
                    decrementCount(mcId.toString());
                }
            }
        }
        saveOpenTickets();
        plugin.getLogger().info("Ticket closed for owner " + ownerKey
                + "; open tickets left: " + openTicketsByUser.size());
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void decrementCount(String userId) {
        Integer current = openTicketsByUser.get(userId);
        if (current == null) {
            return;
        }
        if (current <= 1) {
            openTicketsByUser.remove(userId);
        } else {
            openTicketsByUser.put(userId, current - 1);
        }
    }

    private void saveOpenTickets() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : openTicketsByUser.entrySet()) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(entry.getKey()).append('=').append(entry.getValue());
        }
        plugin.getStorageManager().setData("open-tickets", sb.toString());
    }

    public void shutdown() {
        plugin.getStorageManager().setData("ticket-counter", ticketCounter.get());
        saveOpenTickets();
    }

    public List<UUID> getOpenTicketCreators() {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : openTicketsByUser.entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            try {
                result.add(UUID.fromString(entry.getKey()));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return result;
    }

    public static boolean isUsableSnowflake(String value) {
        return value != null && value.matches(SNOWFLAKE_PATTERN);
    }

    private static String displayLabel(TicketCategory category) {
        return category.emoji.isEmpty()
                ? category.label
                : category.emoji + " " + category.label;
    }
}

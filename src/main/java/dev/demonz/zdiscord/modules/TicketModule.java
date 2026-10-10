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
import java.util.concurrent.atomic.AtomicInteger;

public class TicketModule extends net.dv8tion.jda.api.hooks.ListenerAdapter {

    public static final String PANEL_BUTTON_ID = "zdiscord_create_ticket";
    public static final String PANEL_SELECT_ID = "zdiscord_ticket_category";
    public static final String OWNER_TOPIC_PREFIX = "zdticket:";
    private static final String SNOWFLAKE_PATTERN = "\\d{17,20}";

    private final ZDiscord plugin;
    private final TicketCounts counts = new TicketCounts();
    private volatile boolean running = true;
    private final AtomicInteger ticketCounter = new AtomicInteger(0);
    private final TicketState state;
    private dev.demonz.zdiscord.platform.PlatformAdapter.TaskHandle expiryTimer;

    public TicketModule(ZDiscord plugin) {
        this.plugin = plugin;
        this.state = new TicketState((channel, staff) ->
                plugin.getStorageManager().setData("ticket-claim-" + channel, staff));
    }

    public void init() {
        ticketCounter.set(plugin.getStorageManager().getDataInt("ticket-counter", 0));
        Guild guild = plugin.getBotManager().getGuild();
        if (guild != null) {
            plugin.getBotManager().getJda().addEventListener(this);
            for (TextChannel channel : guild.getTextChannels()) {
                String topic = channel.getTopic();
                if (topic != null && topic.startsWith(OWNER_TOPIC_PREFIX)) {
                    String stored = topic.substring(OWNER_TOPIC_PREFIX.length());
                    java.util.Set<String> keys = new java.util.HashSet<>(java.util.List.of(stored.split(",")));
                    keys.remove("");
                    if (keys.size() == 1) keys = ownerKeys(keys.iterator().next(), null);
                    counts.restore(channel.getId(), keys);
                    long activity = TicketState.createdAt(channel.getId());
                    if (channel.getLatestMessageId() != null)
                        activity = Math.max(activity, TicketState.createdAt(channel.getLatestMessageId()));
                    state.restore(channel.getId(), activity,
                            plugin.getStorageManager().getData("ticket-claim-" + channel.getId()));
                }
            }
        }
        expiryTimer = plugin.getPlatformAdapter().scheduleAsyncTimer(this::closeInactiveTickets, 1200L, 1200L);
        plugin.debug("Ticket module initialised (counter: " + ticketCounter.get()
                + ", categories: " + getCategories().size()
                + ", tracked users: " + counts.snapshot().size() + ")");
    }

    @Override
    public void onChannelDelete(net.dv8tion.jda.api.events.channel.ChannelDeleteEvent event) {
        counts.closed(event.getChannel().getId());
        state.remove(event.getChannel().getId());
    }

    @Override
    public void onMessageReceived(net.dv8tion.jda.api.events.message.MessageReceivedEvent event) {
        if (running && event.isFromGuild())
            state.touch(event.getChannel().getId(), event.getMessage().getTimeCreated().toInstant().toEpochMilli());
    }

    public void closeInactiveTickets() {
        if (!running || !plugin.getBotManager().isConnected()) return;
        double hours = plugin.getConfigManager().getConfig().getDouble("tickets.auto-close-hours", 48);
        for (String id : state.expired(System.currentTimeMillis(), hours)) {
            TextChannel channel = plugin.getBotManager().getJda().getTextChannelById(id);
            if (channel == null) continue;
            if (channel.getLatestMessageId() != null)
                state.touch(id, TicketState.createdAt(channel.getLatestMessageId()));
            if (!beginInactiveClose(id, hours)) continue;
            deleteClosingTicket(channel, "Ticket inactive for " + hours + " hours");
        }
    }

    private synchronized boolean beginInactiveClose(String id, double hours) {
        return running && state.beginExpiry(id, System.currentTimeMillis(), hours, () -> counts.beginClose(id));
    }

    public void deleteClosingTicket(TextChannel channel, String reason) {
        String id = channel.getId();
        if (!running) { finishClose(id, false); return; }
        try {
            channel.delete().reason(reason).queue(success -> finishClose(id, true), error -> {
                finishClose(id, false);
                plugin.getLogger().warning("Could not close ticket " + id + ": " + error.getMessage());
            });
        } catch (RuntimeException error) {
            finishClose(id, false);
            plugin.getLogger().warning("Could not close ticket " + id + ": " + error.getMessage());
        }
    }

    public record ClaimResult(String staffId, boolean newlyClaimed) { }

    public synchronized ClaimResult claimTicket(String channelId, String staffId) {
        if (!running || !counts.isOpen(channelId)) return new ClaimResult(null, false);
        String previous = state.claimant(channelId);
        return new ClaimResult(state.claim(channelId, staffId), previous == null);
    }

    public String getTicketClaimant(String channelId) { return state.claimant(channelId); }

    public boolean isOpenTicket(String channelId) { return running && counts.isOpen(channelId); }

    public void createTicketFromMC(Player player, String subject) {
        if (plugin.getLinkModule() == null || !plugin.getLinkModule().isLinked(player.getUniqueId())) {
            player.sendMessage(plugin.getMessageManager().get("link-required"));
            return;
        }
        String discordId = plugin.getLinkModule().getDiscordId(player.getUniqueId());
        String categoryId = defaultCategoryId();
        String playerName = player.getName();
        String playerId = player.getUniqueId().toString();

        plugin.getPlatformAdapter().runAsync(() -> {
            TicketCategory cat = getCategory(categoryId);
            String effectiveSubject = subject != null && !subject.isBlank()
                    ? subject
                    : (cat != null ? cat.label : "Support");
            TextChannel channel = createTicketChannel(
                    playerName, effectiveSubject, categoryId, discordId, playerId);
            if (channel != null) {
                plugin.getPlatformAdapter().runForEntity(player, () -> player.sendMessage(
                        plugin.getMessageManager().get("ticket-created",
                                "%channel%", channel.getName())));
            } else {
                int max = Math.max(1, plugin.getConfigManager().getInt("tickets.max-per-user", 3));
                String message = counts.atLimit(ownerKeys(playerId, discordId), max)
                        ? plugin.getMessageManager().get("ticket-max-reached", "%max%", String.valueOf(max))
                        : plugin.getMessageManager().get("ticket-create-failed");
                plugin.getPlatformAdapter().runForEntity(player, () -> {
                    if (player.isOnline()) player.sendMessage(message);
                });
            }
        });
    }

    public void createTicket(User user, String subject, SlashCommandInteractionEvent event) {
        createTicket(user, subject, defaultCategoryId(), event);
    }

    public void createTicket(User user, String subject, String categoryId,
                             SlashCommandInteractionEvent event) {
        if (event == null) return;
        event.deferReply(true).queue(hook -> plugin.getPlatformAdapter().runAsync(() ->
                hook.sendMessage(createTicketMessage(user, subject, categoryId)).queue()));
    }

    private String createTicketMessage(User user, String subject, String categoryId) {
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
            int max = Math.max(1, plugin.getConfigManager().getInt("tickets.max-per-user", 3));
            if (counts.atLimit(ownerKeys(user.getId(), user.getId()), max)) {
                return "You have reached the maximum number of open tickets (" + max + ").";
            }
            return "Failed to create ticket. Please contact an admin.";
        }
        plugin.getLogger().info("Ticket " + channel.getName() + " opened for " + user.getName()
                + " (category " + finalCategory + ", channel " + channel.getId() + ")");
        return "Ticket created. See " + channel.getAsMention();
    }

    public String createTicketForCategory(User user, String categoryId) {
        if (getCategory(categoryId) == null) return "That ticket category is no longer available.";
        return createTicketMessage(user, null, categoryId);
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

    private java.util.Set<String> ownerKeys(String owner, String discord) {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (owner != null && !owner.isBlank()) keys.add(owner);
        if (discord != null && !discord.isBlank()) keys.add(discord);
        if (plugin.getLinkModule() != null && owner != null) {
            if (isUuid(owner)) {
                String linked = plugin.getLinkModule().getDiscordId(UUID.fromString(owner));
                if (linked != null) keys.add(linked);
            } else {
                UUID linked = plugin.getLinkModule().getPlayerUUID(owner);
                if (linked != null) keys.add(linked.toString());
            }
        }
        return keys;
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

        if (!running) return null;
        java.util.Set<String> owners = ownerKeys(ownerKey, discordId);
        int max = Math.max(1, plugin.getConfigManager().getInt("tickets.max-per-user", 3));
        if (!counts.reserve(owners, max)) return null;
        int currentTicket;
        synchronized (ticketCounter) {
            currentTicket = ticketCounter.incrementAndGet();
            plugin.getStorageManager().setData("ticket-counter", currentTicket);
        }

        TicketCategory cat = getCategory(categoryId);

        String channelName = "ticket-" + String.format(Locale.ROOT, "%04d", currentTicket)
                + "-" + username.toLowerCase().replaceAll("[^a-z0-9-]", "");

        try {
            var builder = guild.createTextChannel(channelName)
                    .setTopic(OWNER_TOPIC_PREFIX + String.join(",", new java.util.TreeSet<>(owners)));
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
                Member member = guild.retrieveMemberById(discordId).complete();
                if (member != null) {
                    builder = builder.addMemberPermissionOverride(member.getIdLong(),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                            EnumSet.noneOf(Permission.class));
                }
            }

            builder = builder.addMemberPermissionOverride(guild.getSelfMember().getIdLong(),
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                    EnumSet.noneOf(Permission.class));

            TextChannel channel = builder.complete();
            counts.opened(channel.getId(), owners);
            state.restore(channel.getId(), TicketState.createdAt(channel.getId()), null);
            try { sendTicketWelcome(channel, username, subject, cat, currentTicket); }
            catch (Exception welcomeError) { plugin.debug("Ticket welcome failed: " + welcomeError.getMessage()); }
            return channel;
        } catch (Exception e) {
            counts.release(owners);
            plugin.getLogger().warning("Failed to create ticket channel: " + e.getMessage());
            return null;
        }
    }

    private void sendTicketWelcome(TextChannel channel, String username,
                                   String subject, TicketCategory cat, int currentTicket) {
        String color = cat != null ? cat.colorHex
                : plugin.getConfigManager().getString("tickets.panel.color", "#5865F2");
        String categoryLabel = cat != null ? cat.label : "Support";

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor("Support Ticket", null, channel.getGuild().getIconUrl())
                .setTitle(categoryLabel)
                .setDescription(
                        "Hello **" + username + "**, a staff member will be with you shortly.\n"
                                + "Please describe your issue in detail and avoid pinging staff.")
                .addField("Subject", subject != null ? subject : categoryLabel, false)
                .addField("Category", categoryLabel, true)
                .addField("Opened by", "`" + username + "`", true)
                .setColor(safeParseHex(color, 0x5865F2))
                .setFooter("Ticket #" + currentTicket + " \u2022 Use the buttons below to manage")
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
                .setTitle(title)
                .setDescription(description)
                .setColor(safeParseHex(colorHex, 0x5865F2))
                .addField("Members", String.valueOf(guild.getMemberCount()), true)
                .addField("Channels", String.valueOf(guild.getTextChannels().size()), true)
                .addField("Privacy",
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
        embed.addField("Categories", categoryList.toString(), false);

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

    public synchronized boolean beginClose(String channelId) {
        return running && counts.beginClose(channelId);
    }

    public void finishClose(String channelId, boolean deleted) {
        if (deleted) {
            counts.closed(channelId);
            state.remove(channelId);
        }
        else counts.closeFailed(channelId);
    }

    private static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public void shutdown() {
        running = false;
        if (expiryTimer != null) expiryTimer.cancel();
        if (plugin.getBotManager().getJda() != null) {
            plugin.getBotManager().getJda().removeEventListener(this);
        }
        plugin.getStorageManager().setData("ticket-counter", ticketCounter.get());
    }

    public List<UUID> getOpenTicketCreators() {
        List<UUID> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counts.snapshot().entrySet()) {
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

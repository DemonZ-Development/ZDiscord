package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.entity.Player;

import java.awt.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support ticket system module.
 * Creates private Discord channels for player support requests.
 * Ticket counter and open-ticket counts persist via StorageManager.
 */
public class TicketModule {

    private final ZDiscord plugin;
    private final Map<String, Integer> openTicketsByUser = new ConcurrentHashMap<>();
    private int ticketCounter = 0;

    public TicketModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // Load ticket counter from storage
        ticketCounter = plugin.getStorageManager().getDataInt("ticket-counter", 0);

        // Load open ticket counts
        String openData = plugin.getStorageManager().getData("open-tickets", "");
        if (!openData.isEmpty()) {
            for (String entry : openData.split(";")) {
                String[] parts = entry.split("=");
                if (parts.length == 2) {
                    try {
                        openTicketsByUser.put(parts[0], Integer.parseInt(parts[1]));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }

        plugin.debug("Ticket module initialized (counter: " + ticketCounter + ", tracked users: "
                + openTicketsByUser.size() + ")");
    }

    public void reload() {
        // Re-read config values (max-per-user, support-roles, etc.)
        // No need to reload counter — it's always the latest in memory
    }

    /**
     * Create a ticket from a Minecraft player.
     */
    public void createTicketFromMC(Player player, String subject) {
        if (plugin.getLinkModule() == null) {
            player.sendMessage(plugin.getMessageManager().get("link-required"));
            return;
        }

        String discordId = plugin.getLinkModule().getDiscordId(player.getUniqueId());

        int maxTickets = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
        int currentOpen = openTicketsByUser.getOrDefault(player.getUniqueId().toString(), 0);

        if (currentOpen >= maxTickets) {
            player.sendMessage(
                    plugin.getMessageManager().get("ticket-max-reached", "%max%", String.valueOf(maxTickets)));
            return;
        }

        plugin.getPlatformAdapter().runAsync(() -> {
            TextChannel channel = createTicketChannel(player.getName(), subject, discordId);
            if (channel != null) {
                openTicketsByUser.merge(player.getUniqueId().toString(), 1, Integer::sum);
                saveOpenTickets();
                plugin.getPlatformAdapter()
                        .runSync(() -> player.sendMessage(plugin.getMessageManager().get("ticket-created",
                                "%channel%", channel.getName())));
            }
        });
    }

    /**
     * Create a ticket from Discord slash command or button.
     */
    public void createTicket(User user, String subject, SlashCommandInteractionEvent event) {
        int maxTickets = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
        int currentOpen = openTicketsByUser.getOrDefault(user.getId(), 0);

        if (currentOpen >= maxTickets) {
            if (event != null) {
                event.reply("❌ You have reached the maximum number of open tickets (" + maxTickets + ").")
                        .setEphemeral(true).queue();
            }
            return;
        }

        TextChannel channel = createTicketChannel(user.getName(), subject, user.getId());
        if (channel != null) {
            openTicketsByUser.merge(user.getId(), 1, Integer::sum);
            saveOpenTickets();
            if (event != null) {
                event.reply("✅ Ticket created! See " + channel.getAsMention()).setEphemeral(true).queue();
            }
        } else {
            if (event != null) {
                event.reply("❌ Failed to create ticket. Please contact an admin.").setEphemeral(true).queue();
            }
        }
    }

    private TextChannel createTicketChannel(String username, String subject, String discordId) {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null)
            return null;

        String categoryId = plugin.getConfigManager().getString("channels.ticket-category");
        Category category = null;
        if (categoryId != null && !categoryId.isEmpty()) {
            category = guild.getCategoryById(categoryId);
        }

        ticketCounter++;
        // Persist the new counter immediately
        plugin.getStorageManager().setData("ticket-counter", ticketCounter);

        String channelName = "ticket-" + String.format("%04d", ticketCounter) + "-" + username.toLowerCase();

        try {
            var builder = guild.createTextChannel(channelName);
            if (category != null) {
                builder = builder.setParent(category);
            }

            // Set permissions - only ticket creator and support roles can see
            builder = builder.addPermissionOverride(guild.getPublicRole(),
                    EnumSet.noneOf(Permission.class),
                    EnumSet.of(Permission.VIEW_CHANNEL));

            // Add support roles
            for (String roleId : plugin.getConfigManager().getStringList("tickets.support-roles")) {
                var role = guild.getRoleById(roleId);
                if (role != null) {
                    builder = builder.addPermissionOverride(role,
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                            EnumSet.noneOf(Permission.class));
                }
            }

            // Add ticket creator
            if (discordId != null) {
                Member member = guild.getMemberById(discordId);
                if (member != null) {
                    builder = builder.addMemberPermissionOverride(member.getIdLong(),
                            EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND),
                            EnumSet.noneOf(Permission.class));
                }
            }

            TextChannel channel = builder.complete();

            // Send initial embed
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("🎫 Support Ticket #" + ticketCounter)
                    .setDescription("**Subject:** " + subject + "\n**Created by:** " + username)
                    .addField("📝 How to use",
                            "Describe your issue in this channel. A staff member will assist you shortly.", false)
                    .setColor(Color.decode("#5865F2"))
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build())
                    .setActionRow(
                            Button.danger("zdiscord_ticket_close", "🔒 Close Ticket"),
                            Button.secondary("zdiscord_ticket_claim", "✋ Claim Ticket"))
                    .queue();

            return channel;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create ticket channel: " + e.getMessage());
            return null;
        }
    }

    /**
     * Called when a ticket channel is closed/deleted.
     */
    public void onTicketClose(String userId) {
        openTicketsByUser.computeIfPresent(userId, (k, v) -> v <= 1 ? null : v - 1);
        saveOpenTickets();
    }

    private void saveOpenTickets() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : openTicketsByUser.entrySet()) {
            if (sb.length() > 0)
                sb.append(";");
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        plugin.getStorageManager().setData("open-tickets", sb.toString());
    }

    public void shutdown() {
        // Save final state
        plugin.getStorageManager().setData("ticket-counter", ticketCounter);
        saveOpenTickets();
    }
}

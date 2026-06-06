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
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Support ticket system. Each ticket is a private Discord channel
 * accessible to the requester and configured support roles.
 */
public class TicketModule {

    private final ZDiscord plugin;
    private final Map<String, Integer> openTicketsByUser = new ConcurrentHashMap<>();
    private int ticketCounter = 0;

    public TicketModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        ticketCounter = plugin.getStorageManager().getDataInt("ticket-counter", 0);
        String openData = plugin.getStorageManager().getData("open-tickets", "");
        openTicketsByUser.clear();
        if (!openData.isEmpty()) {
            // Format: user1=count1;user2=count2 — we use a simple, non-conflicting
            // serialisation since usernames/UUIDs don't contain '=' or ';'.
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
        plugin.debug("Ticket module initialised (counter: " + ticketCounter
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

        if (!canCreate(player.getUniqueId().toString(), discordId)) {
            int max = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
            player.sendMessage(plugin.getMessageManager().get(
                    "ticket-max-reached", "%max%", String.valueOf(max)));
            return;
        }

        plugin.getPlatformAdapter().runAsync(() -> {
            TextChannel channel = createTicketChannel(player.getName(), subject, discordId);
            if (channel != null) {
                markOpened(player.getUniqueId().toString());
                if (discordId != null) {
                    markOpened(discordId);
                }
                plugin.getPlatformAdapter().runSync(() -> player.sendMessage(
                        plugin.getMessageManager().get("ticket-created",
                                "%channel%", channel.getName())));
            }
        });
    }

    public void createTicket(User user, String subject, SlashCommandInteractionEvent event) {
        if (!canCreate(user.getId(), user.getId())) {
            int max = plugin.getConfigManager().getInt("tickets.max-per-user", 3);
            if (event != null) {
                event.reply("You have reached the maximum number of open tickets ("
                        + max + ").").setEphemeral(true).queue();
            }
            return;
        }

        TextChannel channel = createTicketChannel(user.getName(), subject, user.getId());
        if (channel != null) {
            markOpened(user.getId());
            if (event != null) {
                event.reply("Ticket created. See " + channel.getAsMention())
                        .setEphemeral(true).queue();
            }
        } else if (event != null) {
            event.reply("Failed to create ticket. Please contact an admin.")
                    .setEphemeral(true).queue();
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

    private TextChannel createTicketChannel(String username, String subject, String discordId) {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            return null;
        }

        String categoryId = plugin.getConfigManager().getString("channels.ticket-category");
        Category category = null;
        if (categoryId != null && !categoryId.isEmpty()) {
            category = guild.getCategoryById(categoryId);
        }

        ticketCounter++;
        plugin.getStorageManager().setData("ticket-counter", ticketCounter);

        String channelName = "ticket-" + String.format("%04d", ticketCounter)
                + "-" + username.toLowerCase().replaceAll("[^a-z0-9-]", "");

        try {
            var builder = guild.createTextChannel(channelName);
            if (category != null) {
                builder = builder.setParent(category);
            }

            builder = builder.addPermissionOverride(guild.getPublicRole(),
                    EnumSet.noneOf(Permission.class),
                    EnumSet.of(Permission.VIEW_CHANNEL));

            for (String roleId : plugin.getConfigManager().getStringList("tickets.support-roles")) {
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

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Support Ticket #" + ticketCounter)
                    .setDescription("**Subject:** " + subject + "\n**Created by:** " + username)
                    .addField("How to use",
                            "Describe your issue in this channel. A staff member will assist you shortly.",
                            false)
                    .setColor(ColorUtil.parseHex("#5865F2"))
                    .setTimestamp(Instant.now());

            channel.sendMessageEmbeds(embed.build())
                    .setActionRow(
                            Button.danger("zdiscord_ticket_close", "Close Ticket"),
                            Button.secondary("zdiscord_ticket_claim", "Claim Ticket"))
                    .queue();

            return channel;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to create ticket channel: " + e.getMessage());
            return null;
        }
    }

    public void onTicketClose(String userId) {
        // Decrement under both the Discord key and the linked Minecraft UUID
        // (if any) so a Minecraft-initiated ticket's count drops when the
        // channel is closed from Discord.
        decrementCount(userId);
        if (plugin.getLinkModule() != null) {
            UUID mcId = plugin.getLinkModule().getPlayerUUID(userId);
            if (mcId != null) {
                decrementCount(mcId.toString());
            }
        }
        saveOpenTickets();
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
        plugin.getStorageManager().setData("ticket-counter", ticketCounter);
        saveOpenTickets();
    }
}

package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.awt.*;
import java.time.Instant;

/**
 * Handles ticket panel button interactions (close, claim).
 */
public class TicketButtonListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public TicketButtonListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();

        switch (id) {
            case "zdiscord_ticket_close":
                handleClose(event);
                break;
            case "zdiscord_ticket_claim":
                handleClaim(event);
                break;
            default:
                // Not our button — ignore
                break;
        }
    }

    private void handleClose(ButtonInteractionEvent event) {
        if (event.getMember() == null)
            return;

        TextChannel channel = event.getChannel().asTextChannel();
        String channelName = channel.getName();

        // Only allow closing in ticket channels
        if (!channelName.startsWith("ticket-")) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        // Check permissions — ticket creator or support staff
        boolean isSupport = false;
        for (String roleId : plugin.getConfigManager().getStringList("tickets.support-roles")) {
            if (event.getMember().getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
                isSupport = true;
                break;
            }
        }

        if (!isSupport && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Only support staff can close tickets.").setEphemeral(true).queue();
            return;
        }

        // Send closing embed
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔒 Ticket Closed")
                .setDescription("This ticket has been closed by " + event.getMember().getEffectiveName() + ".")
                .setColor(Color.decode("#E74C3C"))
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();

        // Notify the ticket module to decrement counter
        if (plugin.getTicketModule() != null) {
            // Try to figure out the user who owns this ticket from channel permissions
            for (var override : channel.getMemberPermissionOverrides()) {
                Member member = override.getMember();
                if (member != null && !member.getUser().isBot()) {
                    plugin.getTicketModule().onTicketClose(member.getId());
                    break;
                }
            }
        }

        // Delete the channel after a short delay
        plugin.getPlatformAdapter().runLater(() -> {
            try {
                channel.delete().reason("Ticket closed by " + event.getMember().getEffectiveName()).queue(
                        success -> plugin.debug("Ticket channel " + channelName + " deleted."),
                        error -> plugin.getLogger().warning("Failed to delete ticket channel: " + error.getMessage()));
            } catch (Exception e) {
                plugin.getLogger().warning("Error deleting ticket channel: " + e.getMessage());
            }
        }, 100L); // 5 seconds delay
    }

    private void handleClaim(ButtonInteractionEvent event) {
        if (event.getMember() == null)
            return;

        // Verify the button was clicked in a ticket channel
        String channelName = event.getChannel().getName();
        if (!channelName.startsWith("ticket-")) {
            event.reply("❌ This is not a ticket channel.").setEphemeral(true).queue();
            return;
        }

        // Check if staff member
        boolean isSupport = false;
        for (String roleId : plugin.getConfigManager().getStringList("tickets.support-roles")) {
            if (event.getMember().getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
                isSupport = true;
                break;
            }
        }

        if (!isSupport && !event.getMember().hasPermission(Permission.ADMINISTRATOR)) {
            event.reply("❌ Only support staff can claim tickets.").setEphemeral(true).queue();
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✋ Ticket Claimed")
                .setDescription("**" + event.getMember().getEffectiveName()
                        + "** has claimed this ticket and will assist you shortly.")
                .setColor(Color.decode("#2ECC71"))
                .setTimestamp(Instant.now());

        event.replyEmbeds(embed.build()).queue();
    }
}

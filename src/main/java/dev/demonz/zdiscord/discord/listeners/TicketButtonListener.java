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

package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.Permission;

import java.util.List;

/**
 * Handles ticket panel button interactions (close, claim).
 */
public class TicketButtonListener extends ListenerAdapter {

    private static final long DELETE_DELAY_TICKS = 100L; // 5 seconds

    private final ZDiscord plugin;

    public TicketButtonListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        switch (event.getComponentId()) {
            case "zdiscord_ticket_close":
                handleClose(event);
                break;
            case "zdiscord_ticket_claim":
                handleClaim(event);
                break;
            default:
                break;
        }
    }

    private void handleClose(ButtonInteractionEvent event) {
        if (event.getMember() == null) {
            return;
        }
        TextChannel channel = event.getChannel().asTextChannel();
        if (!channel.getName().startsWith("ticket-")) {
            event.reply(plugin.getMessageManager().getRaw("ticket-not-ticket-channel"))
                    .setEphemeral(true).queue();
            return;
        }
        if (!isSupport(event.getMember())) {
            event.reply(plugin.getMessageManager().getRaw("ticket-only-staff"))
                    .setEphemeral(true).queue();
            return;
        }

        event.reply(plugin.getMessageManager().get(
                "ticket-closed-message",
                "%staff%", event.getMember().getEffectiveName())).queue();

        if (plugin.getTicketModule() != null) {
            decrementForTicketCreator(channel);
        }

        plugin.getPlatformAdapter().runLater(
                () -> channel.delete().reason("Ticket closed by "
                        + event.getMember().getEffectiveName()).queue(
                        success -> plugin.debug("Ticket channel " + channel.getName() + " deleted."),
                        error -> plugin.getLogger().warning("Failed to delete ticket channel: "
                                + error.getMessage())),
                DELETE_DELAY_TICKS);
    }

    private void handleClaim(ButtonInteractionEvent event) {
        if (event.getMember() == null) {
            return;
        }
        if (!event.getChannel().getName().startsWith("ticket-")) {
            event.reply(plugin.getMessageManager().getRaw("ticket-not-ticket-channel"))
                    .setEphemeral(true).queue();
            return;
        }
        if (!isSupport(event.getMember())) {
            event.reply(plugin.getMessageManager().getRaw("ticket-only-staff"))
                    .setEphemeral(true).queue();
            return;
        }

        event.reply(plugin.getMessageManager().get(
                "ticket-claimed", "%staff%", event.getMember().getEffectiveName())).queue();
    }

    private boolean isSupport(Member member) {
        if (member.hasPermission(Permission.ADMINISTRATOR)) {
            return true;
        }
        List<String> supportRoles = plugin.getConfigManager()
                .getStringList("tickets.support-roles");
        for (String roleId : supportRoles) {
            if (member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Walk the channel's permission overrides to find the user the
     * ticket was created for and decrement their open-ticket count.
     */
    private void decrementForTicketCreator(TextChannel channel) {
        for (var override : channel.getMemberPermissionOverrides()) {
            Member member = override.getMember();
            if (member != null && !member.getUser().isBot()) {
                plugin.getTicketModule().onTicketClose(member.getId());
                return;
            }
        }
    }
}

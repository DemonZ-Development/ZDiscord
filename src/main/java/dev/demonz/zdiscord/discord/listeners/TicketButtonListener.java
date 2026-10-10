package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.modules.TicketModule;
import dev.demonz.zdiscord.discord.TicketTranscriptService;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import java.util.List;

public class TicketButtonListener extends ListenerAdapter {

    private static final long DELETE_DELAY_TICKS = 100L;
    private static final String QUICK_ACTION = "quick";

    private final ZDiscord plugin;

    public TicketButtonListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (!id.startsWith(TicketModule.PANEL_BUTTON_ID + ":")) return;
        if (!isConfiguredGuild(event)) {
            event.reply("Use ticket controls in the configured Discord server.").setEphemeral(true).queue();
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }

        switch (id.substring(TicketModule.PANEL_BUTTON_ID.length() + 1)) {
            case QUICK_ACTION -> handleQuickOpen(event);
            case "close" -> handleClose(event);
            case "claim" -> handleClaim(event);
            case "transcript" -> handleTranscript(event);
            default -> event.reply("This ticket control is no longer available.").setEphemeral(true).queue();
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!TicketModule.PANEL_SELECT_ID.equals(event.getComponentId())) return;
        if (!isConfiguredGuild(event) || event.getValues().isEmpty()) {
            event.reply("Use ticket controls in the configured Discord server.").setEphemeral(true).queue();
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }

        createTicketFromInteraction(event, event.getUser(), event.getValues().get(0));
    }

    private void handleQuickOpen(ButtonInteractionEvent event) {
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }

        String defaultId = plugin.getTicketModule().defaultCategoryId();
        if (defaultId == null) {
            event.reply("No default ticket category is configured.").setEphemeral(true).queue();
            return;
        }
        createTicketFromInteraction(event, event.getUser(), defaultId);
    }

    private void createTicketFromInteraction(GenericInteractionCreateEvent event, User user, String categoryId) {
        if (event instanceof ButtonInteractionEvent button) {
            button.deferReply(true).queue(hook -> completeTicketCreate(hook, user, categoryId));
        } else if (event instanceof StringSelectInteractionEvent select) {
            select.deferReply(true).queue(hook -> completeTicketCreate(hook, user, categoryId));
        }
    }

    private void completeTicketCreate(InteractionHook hook, User user, String categoryId) {
        TicketModule tickets = plugin.getTicketModule();
        if (tickets == null) {
            hook.editOriginal("The ticket system is disabled.").queue();
            return;
        }
        plugin.getPlatformAdapter().runAsync(() -> {
            String message = tickets.createTicketForCategory(user, categoryId);
            hook.editOriginal(message).queue();
        });
    }

    private void handleClose(ButtonInteractionEvent event) {
        if (event.getMember() == null) return;

        TextChannel channel = event.getChannel().asTextChannel();
        if (!isTicketChannel(channel)) {
            event.reply(plugin.getMessageManager().getRaw("ticket-not-ticket-channel"))
                    .setEphemeral(true).queue();
            return;
        }
        if (!isSupport(event.getMember())) {
            event.reply(plugin.getMessageManager().getRaw("ticket-only-staff"))
                    .setEphemeral(true).queue();
            return;
        }

        TicketModule tickets = plugin.getTicketModule();
        if (tickets == null || !tickets.beginClose(channel.getId())) {
            event.reply("This ticket is already closing or is no longer tracked.").setEphemeral(true).queue();
            return;
        }
        event.reply(plugin.getMessageManager().get(
                "ticket-closed-message", "%staff%", event.getMember().getEffectiveName())).queue(ignored -> {
            try { plugin.getPlatformAdapter().runLater(
                () -> tickets.deleteClosingTicket(channel, "Ticket closed by "
                        + event.getMember().getEffectiveName()), DELETE_DELAY_TICKS); }
            catch (RuntimeException error) { tickets.finishClose(channel.getId(), false); }
        }, error -> tickets.finishClose(channel.getId(), false));
    }

    private void handleClaim(ButtonInteractionEvent event) {
        if (event.getMember() == null) return;

        TextChannel channel = event.getChannel().asTextChannel();
        if (!isTicketChannel(channel)) {
            event.reply(plugin.getMessageManager().getRaw("ticket-not-ticket-channel"))
                    .setEphemeral(true).queue();
            return;
        }
        if (!isSupport(event.getMember())) {
            event.reply(plugin.getMessageManager().getRaw("ticket-only-staff"))
                    .setEphemeral(true).queue();
            return;
        }

        TicketModule tickets = plugin.getTicketModule();
        String staffId = event.getUser().getId();
        String name = event.getMember().getEffectiveName();
        event.deferReply(true).queue(hook -> plugin.getPlatformAdapter().runAsync(() -> {
            var claim = tickets.claimTicket(channel.getId(), staffId);
            if (claim.staffId() == null) {
                hook.editOriginal("This ticket is closing or is no longer tracked.").queue();
            } else if (!claim.newlyClaimed()) {
                hook.editOriginal("This ticket is already assigned to <@" + claim.staffId() + ">.")
                        .setAllowedMentions(java.util.Collections.emptyList()).queue();
            } else {
                channel.sendMessage(plugin.getMessageManager().get(
                        "ticket-claimed", "%staff%", name)).setAllowedMentions(java.util.Collections.emptyList())
                        .queue(ignored -> hook.editOriginal("Ticket assigned to you.").queue(),
                                error -> hook.editOriginal("Ticket assigned to you, but the public announcement could not be sent.").queue());
            }
        }));
    }

    private void handleTranscript(ButtonInteractionEvent event) {
        if (event.getMember() == null) return;

        TextChannel channel = event.getChannel().asTextChannel();
        if (!isTicketChannel(channel)) {
            event.reply(plugin.getMessageManager().getRaw("ticket-not-ticket-channel"))
                    .setEphemeral(true).queue();
            return;
        }
        if (!isSupport(event.getMember())) {
            event.reply(plugin.getMessageManager().getRaw("ticket-only-staff"))
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue(hook -> TicketTranscriptService.export(channel, plugin.getPlatformAdapter()::runAsync)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        plugin.getLogger().warning("Ticket transcript failed for " + channel.getId() + ": " + error.getMessage());
                        hook.editOriginal("Could not finish the transcript. Check the bot's history and attachment permissions; any uploaded parts remain in this channel.").queue();
                    } else if (result.messages() == 0) {
                        hook.editOriginal("Nothing to transcribe — this channel has no messages yet.").queue();
                    } else {
                        hook.editOriginal("Transcript generated: " + result.messages() + " messages in "
                                + result.urls().size() + " file(s), posted in " + channel.getAsMention() + ".").queue();
                    }
                }));
    }

    private boolean isTicketChannel(TextChannel channel) {
        return channel != null && plugin.getTicketModule() != null
                && plugin.getTicketModule().isOpenTicket(channel.getId());
    }

    private boolean isConfiguredGuild(GenericInteractionCreateEvent event) {
        return event.getGuild() != null && event.getGuild().getId().equals(
                plugin.getConfigManager().getString("bot.guild-id"));
    }

    private boolean isSupport(Member member) {
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;

        List<String> supportRoles = plugin.getConfigManager().getStringList("tickets.support-roles");
        for (String roleId : supportRoles) {
            if (member.getRoles().stream().anyMatch(r -> r.getId().equals(roleId))) {
                return true;
            }
        }
        return false;
    }

}

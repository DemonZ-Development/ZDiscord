package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.modules.TicketModule;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.utils.FileUpload;

import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
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
        if (!id.startsWith(TicketModule.PANEL_BUTTON_ID)) {
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }

        String action = id.substring(TicketModule.PANEL_BUTTON_ID.length() + 1);
        switch (action) {
            case QUICK_ACTION:
                handleQuickOpen(event);
                break;
            case "close":
                handleClose(event);
                break;
            case "claim":
                handleClaim(event);
                break;
            case "transcript":
                handleTranscript(event);
                break;
            default:
                break;
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!TicketModule.PANEL_SELECT_ID.equals(event.getComponentId())) {
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }
        String categoryId = event.getValues().get(0);
        plugin.getTicketModule().createTicketForCategory(event.getUser(), categoryId);
        event.reply("Your support ticket has been created. Check the new channel.")
                .setEphemeral(true).queue();
    }

    private void handleQuickOpen(ButtonInteractionEvent event) {
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled.").setEphemeral(true).queue();
            return;
        }
        String defaultId = plugin.getTicketModule().defaultCategoryId();
        if (defaultId == null) {
            event.reply("No default ticket category is configured.")
                    .setEphemeral(true).queue();
            return;
        }
        plugin.getTicketModule().createTicketForCategory(event.getUser(), defaultId);
        event.reply("Your support ticket has been created. Check the new channel.")
                .setEphemeral(true).queue();
    }

    private void handleClose(ButtonInteractionEvent event) {
        if (event.getMember() == null) {
            return;
        }
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

        event.reply(plugin.getMessageManager().get(
                "ticket-claimed", "%staff%", event.getMember().getEffectiveName())).queue();
    }

    private void handleTranscript(ButtonInteractionEvent event) {
        if (event.getMember() == null) {
            return;
        }
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

        event.deferReply().setEphemeral(true).queue();
        channel.getHistoryBefore(channel.getLatestMessageId(), 100).queue(
                history -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("# Ticket Transcript - ").append(channel.getName()).append("\n\n");
                    List<Message> messages = history.getRetrievedHistory();
                    for (int i = messages.size() - 1; i >= 0; i--) {
                        Message msg = messages.get(i);
                        String timestamp = msg.getTimeCreated()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                        String author = msg.getAuthor().getEffectiveName();
                        String content = msg.getContentDisplay();
                        sb.append("**").append(author).append("** (").append(timestamp).append("):\n")
                                .append(content).append("\n\n");
                    }
                    byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                    channel.sendFiles(FileUpload.fromData(bytes,
                            "transcript-" + channel.getName() + ".md"))
                            .queue(msg -> {
                                String url = msg.getAttachments().isEmpty()
                                        ? "No attachment URL"
                                        : msg.getAttachments().get(0).getUrl();
                                event.getHook().sendMessage(
                                        "Transcript generated: " + url)
                                        .setEphemeral(true).queue();
                            },
                            err -> event.getHook().sendMessage(
                                    "Failed to upload transcript: " + err.getMessage())
                                    .setEphemeral(true).queue());
                },
                err -> event.getHook().sendMessage(
                        "Failed to fetch message history: " + err.getMessage())
                        .setEphemeral(true).queue());
    }

    private boolean isTicketChannel(TextChannel channel) {
        return channel != null && channel.getName().startsWith("ticket-");
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


    private void decrementForTicketCreator(TextChannel channel) {
        for (var override : channel.getMemberPermissionOverrides()) {
            Member member = override.getMember();
            if (member != null && !member.getUser().isBot()) {
                plugin.getTicketModule().onTicketClose(member.getId());
                return;
            }
        }
    }


    @SuppressWarnings("unused")
    private static final Button LEGACY_CLOSE = Button.danger(
            "zdiscord_ticket_close", "Close");
}

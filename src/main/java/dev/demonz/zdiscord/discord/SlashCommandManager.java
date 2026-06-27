package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import dev.demonz.zdiscord.util.PlayerProfileBuilder;
import dev.demonz.zdiscord.util.StatusEmbedBuilder;
import dev.demonz.zdiscord.util.TPSUtil;
import dev.demonz.zdiscord.util.ZLogger;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;


public class SlashCommandManager extends ListenerAdapter {

    private final ZDiscord plugin;

    public SlashCommandManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        Guild guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            plugin.getLogger().warning("Could not register slash commands: guild not found.");
            return;
        }

        guild.updateCommands().addCommands(
                Commands.slash("status", "View the Minecraft server status"),
                Commands.slash("players", "View online players"),
                Commands.slash("tps", "View server performance (TPS and memory)"),
                Commands.slash("link", "Link your Discord account to Minecraft")
                        .addOption(OptionType.STRING, "code",
                                "Your link code from /zdiscord link", true),
                Commands.slash("ticket", "Create a support ticket")
                        .addOption(OptionType.STRING, "subject", "Ticket subject", true),
                Commands.slash("panel", "(Re)post the ticket panel in this channel"),
                Commands.slash("leaderboard", "View player leaderboards")
                        .addOption(OptionType.STRING, "stat",
                                "Stat to view (kills, deaths, playtime)", true),
                Commands.slash("setup", "Open the ZDiscord setup wizard")
                        .addOption(OptionType.STRING, "module",
                                "Quick setup: module name (chat, status, events, etc.)",
                                false, true)
                        .addOption(OptionType.CHANNEL, "channel",
                                "Quick setup: target channel", false),
                Commands.slash("profile", "View a Minecraft player's profile card")
                        .addOption(OptionType.STRING, "player",
                                "Player name (omit for yourself)", false),
                Commands.slash("seen", "When was a player last online?")
                        .addOption(OptionType.STRING, "player",
                                "Player name", true),
                Commands.slash("following", "List the Minecraft players you follow"),
                Commands.slash("confess", "Post an anonymous confession to the confessions channel")
                        .addOption(OptionType.STRING, "message",
                                "What do you want to confess?", true),
                Commands.slash("unfollow", "Stop following a Minecraft player")
                        .addOption(OptionType.STRING, "player",
                                "Player name to unfollow", true))
                .queue(
                        success -> ZLogger.info(ZLogger.Category.COMMANDS,
                                "Registered " + success.size() + " slash commands."),
                        error -> ZLogger.warn(ZLogger.Category.COMMANDS,
                                "Failed to register slash commands: " + error.getMessage()));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {


        if ("setup".equals(event.getName())) {
            return;
        }
        switch (event.getName()) {
            case "status":
                handleStatus(event);
                break;
            case "players":
                handlePlayers(event);
                break;
            case "tps":
                handleTps(event);
                break;
            case "link":
                handleLink(event);
                break;
            case "ticket":
                handleTicket(event);
                break;
            case "panel":
                handlePanel(event);
                break;
            case "leaderboard":
                handleLeaderboard(event);
                break;
            case "profile":
                handleProfile(event);
                break;
            case "seen":
                handleSeen(event);
                break;
            case "following":
                handleFollowing(event);
                break;
            case "confess":
                handleConfess(event);
                break;
            case "unfollow":
                handleUnfollow(event);
                break;
            default:
                break;
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        String title = plugin.getMessageManager().getRaw("slash-status-title");
        StatusEmbedBuilder.StatusContext ctx = StatusEmbedBuilder.StatusContext.capture(
                plugin.getBotManager()::getGuild,
                title,
                plugin.getConfigManager().getString("status.embed.color", "#5865F2"),
                plugin.getConfigManager().getString("status.embed.server-ip", "play.yourserver.com"),
                plugin.getConfigManager().getInt("status.update-interval", 30),
                plugin.getConfigManager().getBoolean("status.embed.show-players", true),
                plugin.getConfigManager().getBoolean("status.embed.show-tps", true),
                plugin.getConfigManager().getBoolean("status.embed.show-memory", true),
                plugin.getConfigManager().getDouble("performance.tps-warning", 18.0),
                plugin.getConfigManager().getDouble("performance.tps-critical", 15.0));

        event.replyEmbeds(StatusEmbedBuilder.build(ctx)).queue();
    }

    private void handlePlayers(SlashCommandInteractionEvent event) {
        var players = Bukkit.getOnlinePlayers();
        if (players.isEmpty()) {
            event.reply("No players are currently online.").setEphemeral(true).queue();
            return;
        }

        String playerList = players.stream()
                .map(Player::getName)
                .sorted()
                .collect(Collectors.joining("\n", "", ""));

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getMessageManager().getRaw("slash-players-title")
                        + " (" + players.size() + "/" + Bukkit.getMaxPlayers() + ")")
                .setDescription(playerList)
                .setColor(ColorUtil.parseHex("#2ECC71"))
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleTps(SlashCommandInteractionEvent event) {
        double[] tps = TPSUtil.getTPS();
        Runtime runtime = Runtime.getRuntime();
        long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMb = runtime.maxMemory() / 1024 / 1024;
        int memPercent = maxMb > 0 ? (int) ((usedMb * 100.0) / maxMb) : 0;

        double tpsWarning = plugin.getConfigManager().getDouble("performance.tps-warning", 18.0);
        double tpsCritical = plugin.getConfigManager().getDouble("performance.tps-critical", 15.0);
        int memWarning = plugin.getConfigManager().getInt("performance.memory-warning", 80);

        int color;
        if (tps[0] >= tpsWarning && memPercent < memWarning) {
            color = 0x2ECC71;
        } else if (tps[0] >= tpsCritical && memPercent < 90) {
            color = 0xF1C40F;
        } else {
            color = 0xE74C3C;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(plugin.getMessageManager().getRaw("slash-tps-title"))
                .setColor(color)
                .addField("TPS (1m / 5m / 15m)",
                        String.format("`%.2f` / `%.2f` / `%.2f`", tps[0], tps[1], tps[2]), false)
                .addField("Memory",
                        usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)", true)
                .addField("Threads", String.valueOf(Thread.activeCount()), true)
                .setTimestamp(Instant.now());
        event.replyEmbeds(embed.build()).queue();
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        if (plugin.getLinkModule() == null) {
            event.reply("Account linking is disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        String code = event.getOption("code").getAsString();
        boolean success = plugin.getLinkModule().processLink(
                event.getUser().getId(), event.getUser().getName(), code);
        if (success) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(plugin.getMessageManager().getRaw("slash-link-title"))
                    .setDescription("Your Discord account has been linked to your Minecraft account.")
                    .setColor(ColorUtil.parseHex("#2ECC71"))
                    .setTimestamp(Instant.now());
            event.replyEmbeds(embed.build()).setEphemeral(true).queue();
        } else {
            event.reply("Invalid or expired link code. Run `/zdiscord link` in Minecraft first.")
                    .setEphemeral(true).queue();
        }
    }

    private void handleTicket(SlashCommandInteractionEvent event) {
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        String subject = event.getOption("subject").getAsString();
        plugin.getTicketModule().createTicket(event.getUser(), subject, event);
    }

    private void handlePanel(SlashCommandInteractionEvent event) {
        if (event.getMember() == null
                || !event.getMember().hasPermission(net.dv8tion.jda.api.Permission.ADMINISTRATOR)) {
            event.reply("You need **Administrator** permission to post the panel.")
                    .setEphemeral(true).queue();
            return;
        }
        if (plugin.getTicketModule() == null) {
            event.reply("The ticket system is disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        if (event.getChannelType() != net.dv8tion.jda.api.entities.channel.ChannelType.TEXT) {
            event.reply("The panel can only be posted in a text channel.")
                    .setEphemeral(true).queue();
            return;
        }
        net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                event.getChannel().asTextChannel();
        plugin.getTicketModule().postPanel(channel);
        event.reply("Ticket panel posted in " + channel.getAsMention() + ".")
                .setEphemeral(true).queue();
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        if (plugin.getLeaderboardModule() == null) {
            event.reply("Leaderboards are disabled in config.yml.")
                    .setEphemeral(true).queue();
            return;
        }
        String stat = event.getOption("stat").getAsString();
        plugin.getLeaderboardModule().sendLeaderboard(event, stat);
    }

    private void handleProfile(SlashCommandInteractionEvent event) {



        event.deferReply().queue();
        String queryName = event.getOption("player") == null
                ? null
                : event.getOption("player").getAsString();
        String discordTag = event.getUser().getAsTag();

        plugin.getPlatformAdapter().runAsync(() -> {
            OfflinePlayer target = resolveProfileTarget(queryName, event.getUser().getId());
            if (target == null) {
                String msg = queryName == null
                        ? "You don't have a linked Minecraft account yet. "
                                + "Run `/link <code>` after `/zdiscord link` in-game, "
                                + "or pass `player:<name>` to look up someone else."
                        : "No player named **" + queryName + "** has joined this server before.";
                event.getHook().sendMessage(msg).setEphemeral(true).queue();
                return;
            }

            PlayerProfileBuilder.Profile profile =
                    PlayerProfileBuilder.build(plugin, target);


            if (profile.discordId != null && plugin.getBotManager().isConnected()) {
                try {
                    net.dv8tion.jda.api.entities.User jdaUser =
                            plugin.getBotManager().getJda()
                                    .retrieveUserById(profile.discordId).complete();
                    if (jdaUser != null) {
                        profile.discordUsername = jdaUser.getAsTag();
                    }
                } catch (Exception e) {
                    plugin.debug("Failed to resolve Discord user for profile: "
                            + e.getMessage());
                }
            }

            EmbedBuilder embed = PlayerProfileBuilder.toEmbed(plugin, profile, discordTag);

            if (plugin.getFollowModule() != null) {
                boolean following = plugin.getFollowModule()
                        .isFollowing(profile.uuid, event.getUser().getId());
                var rows = new java.util.ArrayList<net.dv8tion.jda.api.interactions.components.LayoutComponent>();
                rows.add(net.dv8tion.jda.api.interactions.components.ActionRow.of(
                        following
                                ? plugin.getFollowModule().buildUnfollowButton(profile.uuid)
                                : plugin.getFollowModule().buildFollowButton(profile.uuid)));
                event.getHook().sendMessageEmbeds(embed.build())
                        .addComponents(rows).queue();
            } else {
                event.getHook().sendMessageEmbeds(embed.build()).queue();
            }
        });
    }

    private OfflinePlayer resolveProfileTarget(String queryName, String requesterDiscordId) {
        if (queryName != null && !queryName.isEmpty()) {
            return PlayerProfileBuilder.findOfflineByName(queryName);
        }

        if (plugin.getLinkModule() == null) {
            return null;
        }
        UUID mcUuid = plugin.getLinkModule().getPlayerUUID(requesterDiscordId);
        if (mcUuid == null) {
            return null;
        }
        return Bukkit.getOfflinePlayer(mcUuid);
    }

    private void handleSeen(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String queryName = event.getOption("player").getAsString();
        plugin.getPlatformAdapter().runAsync(() -> {
            OfflinePlayer target = PlayerProfileBuilder.findOfflineByName(queryName);
            if (target == null) {
                event.getHook().sendMessage("No player named **" + queryName
                        + "** has joined this server before.")
                        .setEphemeral(true).queue();
                return;
            }
            UUID uuid = target.getUniqueId();
            String name = target.getName() != null ? target.getName() : "Unknown";
            long lastSeen = plugin.getStorageManager().getLastSeen(uuid);
            long playtimeSec = plugin.getLeaderboardModule() != null
                    ? plugin.getLeaderboardModule().getStat(uuid, "playtime")
                    : 0L;

            EmbedBuilder embed = new EmbedBuilder()
                    .setAuthor(name + "  \u00B7  Last seen",
                            "https://namemc.com/profile/" + uuid,
                            HeadUtil.avatar(uuid, HeadUtil.SIZE_SMALL))
                    .setColor(ColorUtil.parseHex("#3498DB"))
                    .setTimestamp(Instant.now());

            if (target.isOnline()) {
                embed.setDescription(":green_circle: **" + name + "** is online right now.");
            } else if (lastSeen > 0) {
                embed.setDescription("Last seen: <t:" + (lastSeen / 1000L) + ":R>.")
                        .addField("Last seen", "<t:" + (lastSeen / 1000L) + ":F>", false)
                        .addField("Total playtime",
                                PlayerProfileBuilder.formatDuration(playtimeSec), true)
                        .addField("Sessions", String.valueOf(
                                plugin.getStorageManager().getSessions(uuid)), true);
            } else {
                embed.setDescription("No activity recorded for **" + name + "** yet. "
                        + "This player has never joined the server.");
            }
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
    }

    private void handleFollowing(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String discordId = event.getUser().getId();
        plugin.getPlatformAdapter().runAsync(() -> {
            if (plugin.getFollowModule() == null) {
                event.getHook().sendMessage("The follow feature is disabled.")
                        .setEphemeral(true).queue();
                return;
            }
            java.util.Set<UUID> followed = plugin.getFollowModule().getFollowedPlayers(discordId);
            if (followed.isEmpty()) {
                event.getHook().sendMessage(
                        "You aren't following any Minecraft players. "
                                + "Use `/profile player:<name>` to find someone and hit **Follow**.")
                        .setEphemeral(true).queue();
                return;
            }
            StringBuilder sb = new StringBuilder();
            for (UUID uuid : followed) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                String name = op.getName() != null ? op.getName() : uuid.toString();
                sb.append(":small_blue_diamond: **").append(name).append("**")
                        .append("  (`").append(uuid).append("`)\n");
            }
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Following " + followed.size() + " player"
                            + (followed.size() == 1 ? "" : "s"))
                    .setDescription(sb.toString())
                    .setColor(ColorUtil.parseHex("#9B59B6"))
                    .setTimestamp(Instant.now());
            event.getHook().sendMessageEmbeds(embed.build()).queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (id == null) {
            return;
        }

        if (plugin.getLeaderboardModule() != null
                && plugin.getLeaderboardModule().handleButtonInteraction(event)) {
            return;
        }

        if (id.startsWith(dev.demonz.zdiscord.modules.FollowModule.FOLLOW_BUTTON_ID)
                || id.startsWith(dev.demonz.zdiscord.modules.FollowModule.UNFOLLOW_BUTTON_ID)) {
            if (plugin.getFollowModule() != null) {
                plugin.getFollowModule().handleFollowButton(event);
            } else {
                event.reply("The follow feature is disabled.").setEphemeral(true).queue();
            }
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {

        if (plugin.getLeaderboardModule() != null
                && plugin.getLeaderboardModule().handleSelectInteraction(event)) {
            return;
        }
    }

    private void handleUnfollow(SlashCommandInteractionEvent event) {
        event.deferReply().queue();
        String queryName = event.getOption("player").getAsString();
        String discordId = event.getUser().getId();
        plugin.getPlatformAdapter().runAsync(() -> {
            if (plugin.getFollowModule() == null) {
                event.getHook().sendMessage("The follow feature is disabled.")
                        .setEphemeral(true).queue();
                return;
            }
            OfflinePlayer target = PlayerProfileBuilder.findOfflineByName(queryName);
            if (target == null) {
                event.getHook().sendMessage("No player named **" + queryName
                        + "** has joined this server before.")
                        .setEphemeral(true).queue();
                return;
            }
            if (!plugin.getFollowModule().isFollowing(target.getUniqueId(), discordId)) {
                event.getHook().sendMessage("You aren't following **" + queryName + "**.")
                        .setEphemeral(true).queue();
                return;
            }
            plugin.getFollowModule().unfollow(target.getUniqueId(), discordId);
            event.getHook().sendMessage(":no_bell: You are no longer following **"
                    + queryName + "**.").setEphemeral(true).queue();
        });
    }

    private void handleConfess(SlashCommandInteractionEvent event) {
        String message = event.getOption("message").getAsString();
        plugin.getConfessionModule().postFromDiscord(event, message);
    }
}

package dev.demonz.zdiscord.discord;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.discord.listeners.DiscordChatListener;
import dev.demonz.zdiscord.discord.listeners.DiscordReactionListener;
import dev.demonz.zdiscord.discord.listeners.ReconnectListener;
import dev.demonz.zdiscord.discord.listeners.TicketButtonListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;


public class BotManager {

    private final ZDiscord plugin;
    private JDA jda;

    public BotManager(ZDiscord plugin) {
        this.plugin = plugin;
    }


    public boolean connect() {
        String token = plugin.getConfigManager().getString("bot.token");
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("No bot token configured. Set bot.token in config.yml.");
            return false;
        }

        try {
            jda = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.GUILD_MESSAGE_REACTIONS,
                            GatewayIntent.GUILD_VOICE_STATES,
                            GatewayIntent.MESSAGE_CONTENT)
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(
                            new DiscordChatListener(plugin),
                            new DiscordReactionListener(plugin),
                            new ReconnectListener(plugin),
                            new TicketButtonListener(plugin),
                            plugin.getSlashCommandManager(),
                            plugin.getSetupCommand(),
                            new ListenerAdapter() {
                                @Override
                                public void onReady(ReadyEvent event) {
                                    updateActivity();
                                    plugin.getLogger().info("Connected to Discord as "
                                            + event.getJDA().getSelfUser().getName() + ".");
                                }
                            })
                    .build();




            jda.awaitReady();

            plugin.getLogger().info("Connecting to Discord...");
            return true;
        } catch (InterruptedException e) {
            plugin.getLogger().severe("Interrupted while waiting for Discord connection.");
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Discord: " + e.getMessage());
            return false;
        }
    }


    public void updateActivity() {
        if (jda == null) {
            return;
        }

        String type = plugin.getConfigManager().getString("bot.activity.type", "WATCHING").toUpperCase();
        String text = plugin.getConfigManager().getString("bot.activity.text", "%online% players online")
                .replace("%online%", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(plugin.getServer().getMaxPlayers()));

        Activity activity;
        switch (type) {
            case "PLAYING":
                activity = Activity.playing(text);
                break;
            case "LISTENING":
                activity = Activity.listening(text);
                break;
            case "COMPETING":
                activity = Activity.competing(text);
                break;
            case "WATCHING":
            default:
                activity = Activity.watching(text);
                break;
        }
        jda.getPresence().setActivity(activity);
    }


    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(java.time.Duration.ofSeconds(5))) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                jda.shutdownNow();
                Thread.currentThread().interrupt();
            }
            jda = null;
        }
    }

    public JDA getJda() {
        return jda;
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public TextChannel getTextChannel(String configPath) {
        if (jda == null) {
            return null;
        }
        String channelId = plugin.getConfigManager().getString(configPath);
        if (channelId == null || channelId.isEmpty()) {
            return null;
        }
        try {
            return jda.getTextChannelById(channelId);
        } catch (Exception e) {
            return null;
        }
    }

    public Guild getGuild() {
        if (jda == null) {
            return null;
        }
        String guildId = plugin.getConfigManager().getString("bot.guild-id");
        if (guildId == null || guildId.isEmpty()) {
            return null;
        }
        return jda.getGuildById(guildId);
    }
}

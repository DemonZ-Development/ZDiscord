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

import java.time.Duration;
import java.net.InetSocketAddress;
import java.net.Proxy;
import com.neovisionaries.ws.client.WebSocketFactory;
import okhttp3.OkHttpClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Locale;
import net.dv8tion.jda.internal.utils.JDALogger;

public class BotManager {

    private final ZDiscord plugin;
    private volatile JDA jda;
    private ExecutorService restWorkers;
    private ExecutorService callbackWorkers;
    private boolean closed;

    public BotManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public synchronized boolean connect() {
        if (closed || !plugin.isEnabled()) return false;
        if (isConnected()) return true;
        if (jda != null) disconnect();
        String token = plugin.getConfigManager().getString("bot.token");
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("No bot token configured. Set bot.token in config.yml.");
            return false;
        }

        String guildId = plugin.getConfigManager().getString("bot.guild-id");
        if (guildId == null || guildId.isEmpty() || guildId.equals("YOUR_GUILD_ID_HERE")) {
            plugin.getLogger().warning("No guild-id configured. Set bot.guild-id in config.yml "
                    + "(right-click your server in Discord > Copy ID, Developer Mode must be on).");
            return false;
        }
        try {
            Long.parseLong(guildId.trim());
        } catch (NumberFormatException e) {
            plugin.getLogger().severe("bot.guild-id is not a valid Discord snowflake: "
                    + "'" + guildId + "'. Expected a numeric ID like 123456789012345678.");
            return false;
        }

        try {
            Thread caller = Thread.currentThread();
            ClassLoader previousLoader = caller.getContextClassLoader();
            try {
                caller.setContextClassLoader(plugin.getClass().getClassLoader());
                Class.forName(JDALogger.class.getName(), true, plugin.getClass().getClassLoader());
            } finally {
                caller.setContextClassLoader(previousLoader);
            }
            restWorkers = Executors.newCachedThreadPool(task -> worker(task, "ZDiscord-REST"));
            callbackWorkers = Executors.newFixedThreadPool(2, task -> worker(task, "ZDiscord-Callback"));
            JDABuilder builder = JDABuilder.createDefault(token)
                    .setRateLimitElastic(restWorkers, false)
                    .setCallbackPool(callbackWorkers, false);
            String proxyHost = System.getProperty("https.proxyHost", "");
            if (!proxyHost.isBlank()) {
                int proxyPort = Integer.parseInt(System.getProperty("https.proxyPort", "443"));
                builder.setHttpClientBuilder(new OkHttpClient.Builder().proxy(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort))));
                WebSocketFactory sockets = new WebSocketFactory();
                sockets.getProxySettings().setHost(proxyHost).setPort(proxyPort);
                builder.setWebsocketFactory(sockets);
            }
            jda = builder
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

            plugin.getLogger().info("Connecting to Discord...");
            long deadline = System.currentTimeMillis() + 15_000L;
            while (jda.getStatus() != JDA.Status.CONNECTED
                    && System.currentTimeMillis() < deadline) {
                JDA.Status status = jda.getStatus();
                if (status == JDA.Status.FAILED_TO_LOGIN
                        || status == JDA.Status.SHUTDOWN
                        || status == JDA.Status.DISCONNECTED) {
                    break;
                }
                Thread.sleep(100L);
            }
            if (jda.getStatus() != JDA.Status.CONNECTED) {
                plugin.getLogger().severe("Discord connection did not come up within 15s "
                        + "(status: " + jda.getStatus() + "). Discord features are disabled "
                        + "- check bot.token / guild-id and your network.");
                disconnect();
                return false;
            }
            if (getGuild() == null) {
                plugin.getLogger().severe("The bot cannot access the configured guild. Invite it to that server first.");
                disconnect();
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            plugin.getLogger().severe("Interrupted while waiting for Discord connection.");
            disconnect();
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Discord: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    public void updateActivity() {
        if (jda == null) return;

        String type = plugin.getConfigManager().getString("bot.activity.type", "WATCHING").toUpperCase(Locale.ROOT);
        String text = plugin.getConfigManager().getString("bot.activity.text", "%online% players online")
                .replace("%online%", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                .replace("%max%", String.valueOf(plugin.getServer().getMaxPlayers()));

        Activity activity = switch (type) {
            case "PLAYING" -> Activity.playing(text);
            case "LISTENING" -> Activity.listening(text);
            case "COMPETING" -> Activity.competing(text);
            default -> Activity.watching(text);
        };
        jda.getPresence().setActivity(activity);
    }

    public synchronized void shutdown() {
        closed = true;
        disconnect();
    }

    private void disconnect() {
        try {
            if (jda != null) {
                jda.shutdown();
                jda.awaitShutdown(Duration.ofSeconds(5));
                jda.shutdownNow();
            }
            drain(restWorkers);
            drain(callbackWorkers);
        } catch (InterruptedException e) {
            if (jda != null) jda.shutdownNow();
            if (restWorkers != null) restWorkers.shutdownNow();
            if (callbackWorkers != null) callbackWorkers.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            jda = null;
            restWorkers = null;
            callbackWorkers = null;
        }
    }

    private Thread worker(Runnable task, String name) {
        Thread thread = new Thread(task, name);
        thread.setDaemon(true);
        thread.setContextClassLoader(plugin.getClass().getClassLoader());
        return thread;
    }

    private void drain(ExecutorService workers) throws InterruptedException {
        if (workers == null) return;
        workers.shutdown();
        if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
            workers.shutdownNow();
            if (!workers.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Discord workers did not terminate within the shutdown deadline.");
            }
        }
    }

    public JDA getJda() {
        return jda;
    }

    public boolean isConnected() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public TextChannel getTextChannel(String configPath) {
        if (jda == null) return null;

        String channelId = plugin.getConfigManager().getString(configPath);
        if (channelId == null || channelId.isEmpty()) return null;

        try {
            return jda.getTextChannelById(channelId);
        } catch (Exception e) {
            return null;
        }
    }

    public Guild getGuild() {
        if (jda == null) return null;

        String guildId = plugin.getConfigManager().getString("bot.guild-id");
        if (guildId == null || guildId.isEmpty() || guildId.equals("YOUR_GUILD_ID_HERE")) return null;

        try {
            return jda.getGuildById(guildId.trim());
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("bot.guild-id is not a valid snowflake: " + guildId);
            return null;
        }
    }
}

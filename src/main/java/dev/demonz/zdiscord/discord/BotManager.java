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
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 * Manages the JDA Discord bot lifecycle.
 */
public class BotManager {

    private final ZDiscord plugin;
    private JDA jda;

    public BotManager(ZDiscord plugin) {
        this.plugin = plugin;
    }

    /**
     * Build the JDA instance and block until the gateway is ready.
     *
     * @return true if the connection succeeded
     */
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
                            plugin.getSetupCommand())
                    .build();

            jda.awaitReady();
            updateActivity();

            plugin.getLogger().info("Connected to Discord as " + jda.getSelfUser().getName() + ".");
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Interrupted while waiting for Discord to connect.");
            return false;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Discord: " + e.getMessage());
            return false;
        }
    }

    /**
     * Update bot activity from {@code bot.activity.*} in the config.
     */
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

    /**
     * Shut the JDA instance down and wait briefly for it to flush.
     */
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

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
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bidirectional staff chat between Minecraft and Discord.
 */
public class StaffChatModule extends ListenerAdapter {

    private final ZDiscord plugin;
    private String channelId;
    private final Set<UUID> toggledPlayers = ConcurrentHashMap.newKeySet();

    public StaffChatModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        channelId = plugin.getConfigManager().getString("staff-chat.channel", "");
        if (!channelId.isEmpty() && plugin.getBotManager().isConnected()) {
            plugin.getBotManager().getJda().addEventListener(this);
        }
    }

    public void sendToDiscord(Player player, String message) {
        if (channelId.isEmpty() || !plugin.getBotManager().isConnected()) {
            return;
        }
        var channel = plugin.getBotManager().getJda().getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage("[SC] **" + player.getName() + "**: " + message).queue();
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) {
            return;
        }
        if (channelId == null || !event.getChannel().getId().equals(channelId)) {
            return;
        }
        String name = event.getMember() != null
                ? event.getMember().getEffectiveName()
                : event.getAuthor().getName();
        String msg = event.getMessage().getContentDisplay();

        plugin.getPlatformAdapter().runSync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.hasPermission("zdiscord.staffchat")) {
                    player.sendMessage("§3[SC] §9" + name + "§8: §f" + msg);
                }
            }
        });
    }

    public void broadcastToStaff(Player sender, String message) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("zdiscord.staffchat")) {
                player.sendMessage("§3[SC] §b" + sender.getName() + "§8: §f" + message);
            }
        }
    }

    public boolean isToggled(UUID uuid) {
        return toggledPlayers.contains(uuid);
    }

    public void toggle(UUID uuid) {
        if (!toggledPlayers.add(uuid)) {
            toggledPlayers.remove(uuid);
        }
    }

    public String getChannelId() {
        return channelId;
    }

    public void reload() {
        channelId = plugin.getConfigManager().getString("staff-chat.channel", "");
    }

    public void shutdown() {
        toggledPlayers.clear();
        if (plugin.getBotManager().isConnected()) {
            plugin.getBotManager().getJda().removeEventListener(this);
        }
    }
}

/*
 * Copyright 2026 DemonZ Development
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
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which linked Minecraft players are currently in a configured
 * Discord voice channel and adds a small indicator to their tab-list name.
 */
public class VoiceStatusModule extends ListenerAdapter {

    private final ZDiscord plugin;
    private final Set<UUID> inVoice = ConcurrentHashMap.newKeySet();
    private String voiceChannelId;

    public VoiceStatusModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        voiceChannelId = plugin.getConfigManager().getString("voice-status.channel", "");
        if (voiceChannelId.isEmpty() || !plugin.getBotManager().isConnected()) {
            return;
        }
        plugin.getBotManager().getJda().addEventListener(this);

        var guild = plugin.getBotManager().getGuild();
        if (guild == null) {
            return;
        }
        var vc = guild.getVoiceChannelById(voiceChannelId);
        if (vc != null) {
            for (Member member : vc.getMembers()) {
                markPlayer(member, true);
            }
        }
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        String joinedId = event.getChannelJoined() != null ? event.getChannelJoined().getId() : null;
        String leftId = event.getChannelLeft() != null ? event.getChannelLeft().getId() : null;
        if (voiceChannelId.equals(joinedId)) {
            markPlayer(event.getMember(), true);
        } else if (voiceChannelId.equals(leftId)) {
            markPlayer(event.getMember(), false);
        }
    }

    private void markPlayer(Member member, boolean joined) {
        if (plugin.getLinkModule() == null || member == null) {
            return;
        }
        UUID playerUUID = plugin.getLinkModule().getPlayerUUID(member.getId());
        if (playerUUID == null) {
            return;
        }
        if (joined) {
            inVoice.add(playerUUID);
        } else {
            inVoice.remove(playerUUID);
        }
        plugin.getPlatformAdapter().runSync(() -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) {
                return;
            }
            if (joined) {
                player.setPlayerListName(player.getName() + " \u00A7b(V)");
            } else {
                player.setPlayerListName(player.getName());
            }
        });
    }

    public boolean isInVoice(UUID uuid) {
        return inVoice.contains(uuid);
    }

    public void reload() {
        voiceChannelId = plugin.getConfigManager().getString("voice-status.channel", "");
    }

    public void shutdown() {
        for (UUID uuid : inVoice) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setPlayerListName(p.getName());
            }
        }
        inVoice.clear();
        if (plugin.getBotManager().isConnected()) {
            plugin.getBotManager().getJda().removeEventListener(this);
        }
    }
}

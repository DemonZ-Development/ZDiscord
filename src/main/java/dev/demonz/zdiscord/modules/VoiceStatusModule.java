package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a 🎙️ indicator on players who are in a Discord voice channel.
 * Uses JDA voice events for real-time tracking — no polling.
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

        if (!voiceChannelId.isEmpty() && plugin.getBotManager().isConnected()) {
            plugin.getBotManager().getJda().addEventListener(this);

            // Scan current voice state on init
            var guild = plugin.getBotManager().getGuild();
            if (guild != null) {
                var vc = guild.getVoiceChannelById(voiceChannelId);
                if (vc != null) {
                    for (Member member : vc.getMembers()) {
                        resolveAndMark(member, true);
                    }
                }
            }
        }
    }

    @Override
    public void onGuildVoiceUpdate(GuildVoiceUpdateEvent event) {
        String joinedId = event.getChannelJoined() != null ? event.getChannelJoined().getId() : null;
        String leftId = event.getChannelLeft() != null ? event.getChannelLeft().getId() : null;

        // Player joined our tracked channel
        if (voiceChannelId.equals(joinedId)) {
            resolveAndMark(event.getMember(), true);
        }

        // Player left our tracked channel
        if (voiceChannelId.equals(leftId)) {
            resolveAndMark(event.getMember(), false);
        }
    }

    private void resolveAndMark(Member member, boolean joined) {
        if (plugin.getLinkModule() == null)
            return;

        String discordId = member.getId();
        UUID playerUUID = plugin.getLinkModule().getPlayerUUID(discordId);
        if (playerUUID == null)
            return;

        if (joined) {
            inVoice.add(playerUUID);
        } else {
            inVoice.remove(playerUUID);
        }

        // Update player display
        plugin.getPlatformAdapter().runSync(() -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                String base = player.getName();
                String displayName = joined ? base + " §b🎙" : base;
                player.setPlayerListName(displayName);
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
        // Clean up player list names
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

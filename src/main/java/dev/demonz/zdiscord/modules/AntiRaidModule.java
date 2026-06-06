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
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.EmbedUtil;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Detects mass-join events and toggles a server lockdown.
 */
public class AntiRaidModule {

    private final ZDiscord plugin;
    private final Deque<Long> recentJoins = new ArrayDeque<>();
    private final AtomicBoolean lockdownActive = new AtomicBoolean(false);

    public AntiRaidModule(ZDiscord plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getPlatformAdapter().runAsyncTimer(() -> {
            long windowMs = plugin.getConfigManager().getInt("anti-raid.time-window", 30) * 1000L;
            long cutoff = System.currentTimeMillis() - windowMs;
            synchronized (recentJoins) {
                while (!recentJoins.isEmpty() && recentJoins.peekFirst() < cutoff) {
                    recentJoins.pollFirst();
                }
            }
        }, 200L, 200L);
    }

    public void onPlayerJoin(Player player) {
        if (player.hasPermission("zdiscord.bypass.antiraid")) {
            return;
        }

        if (lockdownActive.get()
                && plugin.getConfigManager().getBoolean("anti-raid.lockdown.kick-new-joins", true)) {
            String kickMsg = plugin.getMessageManager().get("lockdown-kick");
            plugin.getPlatformAdapter().runForEntity(player, () -> player.kickPlayer(kickMsg));
            return;
        }

        long now = System.currentTimeMillis();
        synchronized (recentJoins) {
            recentJoins.addLast(now);
        }

        int maxJoins = plugin.getConfigManager().getInt("anti-raid.max-joins", 10);
        int timeWindow = plugin.getConfigManager().getInt("anti-raid.time-window", 30);

        synchronized (recentJoins) {
            long cutoff = now - (timeWindow * 1000L);
            long recent = recentJoins.stream().filter(t -> t >= cutoff).count();
            if (recent >= maxJoins) {
                enableLockdown();
            }
        }
    }

    private void enableLockdown() {
        if (!lockdownActive.compareAndSet(false, true)) {
            return;
        }
        plugin.getLogger().warning("Anti-raid lockdown enabled; possible raid detected.");

        plugin.getPlatformAdapter().runSync(
                () -> Bukkit.broadcastMessage(plugin.getMessageManager().get("lockdown-enabled")));

        if (plugin.getConfigManager().getBoolean("anti-raid.lockdown.notify-staff", true)) {
            TextChannel channel = resolveEventChannel();
            if (channel != null) {
                int recent = recentJoins.size();
                channel.sendMessageEmbeds(EmbedUtil.simple(
                        "Raid detected - server lockdown",
                        "A possible raid has been detected. The server is now in lockdown. "
                                + "New joins will be kicked until the lockdown is lifted.",
                        new java.awt.Color(0xE74C3C))
                        .addField("Recent joins", String.valueOf(recent), true)
                        .build())
                        .queue();
            }
        }

        int autoLift = plugin.getConfigManager().getInt("anti-raid.lockdown.auto-lift", 300);
        if (autoLift > 0) {
            plugin.getPlatformAdapter().runLater(this::disableLockdown, autoLift * 20L);
        }
    }

    private void disableLockdown() {
        if (!lockdownActive.compareAndSet(true, false)) {
            return;
        }
        plugin.getLogger().info("Anti-raid lockdown lifted.");

        plugin.getPlatformAdapter().runSync(
                () -> Bukkit.broadcastMessage(plugin.getMessageManager().get("lockdown-disabled")));

        TextChannel channel = resolveEventChannel();
        if (channel != null) {
            channel.sendMessageEmbeds(EmbedUtil.simple(
                    "Lockdown lifted",
                    "The server lockdown has been lifted. Normal operations resumed.",
                    new java.awt.Color(0x2ECC71)).build()).queue();
        }

        synchronized (recentJoins) {
            recentJoins.clear();
        }
    }

    public void toggleLockdown(CommandSender sender) {
        if (lockdownActive.get()) {
            disableLockdown();
            sender.sendMessage(plugin.getMessageManager().get("lockdown-disabled"));
        } else {
            enableLockdown();
            sender.sendMessage(plugin.getMessageManager().get("lockdown-enabled"));
        }
    }

    public boolean isLockdownActive() {
        return lockdownActive.get();
    }

    public void reload() {
    }

    public void shutdown() {
    }

    private TextChannel resolveEventChannel() {
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.events");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        return channel;
    }
}

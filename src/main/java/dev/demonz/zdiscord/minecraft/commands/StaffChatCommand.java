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

package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * {@code /sc} - send a message to staff chat, or toggle staff chat mode
 * for subsequent messages.
 */
public class StaffChatCommand implements CommandExecutor, Listener {

    private final ZDiscord plugin;

    public StaffChatCommand(ZDiscord plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().get("player-only"));
            return true;
        }
        if (!sender.hasPermission("zdiscord.staffchat")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        if (plugin.getStaffChatModule() == null) {
            sender.sendMessage("Staff chat is disabled in config.yml.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            plugin.getStaffChatModule().toggle(player.getUniqueId());
            boolean on = plugin.getStaffChatModule().isToggled(player.getUniqueId());
            player.sendMessage(on ? "Staff chat mode: ON" : "Staff chat mode: OFF");
            return true;
        }

        String message = String.join(" ", args);
        plugin.getStaffChatModule().broadcastToStaff(player, message);
        plugin.getStaffChatModule().sendToDiscord(player, message);
        return true;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncPlayerChatEvent event) {
        if (plugin.getStaffChatModule() == null) {
            return;
        }
        if (!plugin.getStaffChatModule().isToggled(event.getPlayer().getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        String message = event.getMessage();

        plugin.getPlatformAdapter().runSync(() -> {
            plugin.getStaffChatModule().broadcastToStaff(player, message);
            plugin.getStaffChatModule().sendToDiscord(player, message);
        });
    }
}

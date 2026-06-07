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

/**
 * {@code /discord} - displays the configured Discord invite link.
 */
public class DiscordCommand implements CommandExecutor {

    private final ZDiscord plugin;

    public DiscordCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String link = plugin.getConfigManager().getString(
                "misc.invite-link", "https://discord.gg/yourserver");
        sender.sendMessage(plugin.getMessageManager().get(
                "discord-link", "%link%", link));
        return true;
    }
}

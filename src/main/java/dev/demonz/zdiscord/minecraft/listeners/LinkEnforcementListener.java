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

package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

/**
 * Rejects login attempts from players whose Minecraft account is not
 * linked to a Discord account, when {@code linking.required} is true.
 */
public class LinkEnforcementListener implements Listener {

    private final ZDiscord plugin;

    public LinkEnforcementListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (plugin.getLinkModule() == null) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("linking.required", false)) {
            return;
        }

        if (event.getPlayer().isOp()
                || event.getPlayer().hasPermission("zdiscord.bypass.link")) {
            return;
        }

        if (!plugin.getLinkModule().isLinked(event.getPlayer().getUniqueId())) {
            String kickMsg = plugin.getMessageManager().get("link-required");
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMsg);
        }
    }
}

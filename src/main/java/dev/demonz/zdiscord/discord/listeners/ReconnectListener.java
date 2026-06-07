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

package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.events.session.SessionRecreateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Re-applies bot state after a JDA session reconnect.
 */
public class ReconnectListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public ReconnectListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onSessionRecreate(SessionRecreateEvent event) {
        plugin.getLogger().info("Discord session reconnected; re-validating modules.");

        plugin.getPlatformAdapter().runAsync(() -> {
            try {
                plugin.getBotManager().updateActivity();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to update bot activity after reconnect: "
                        + e.getMessage());
            }
        });
    }
}

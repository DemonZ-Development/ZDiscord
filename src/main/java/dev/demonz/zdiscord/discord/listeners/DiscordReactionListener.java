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

package dev.demonz.zdiscord.discord.listeners;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Forwards Discord reaction events to the reaction-role module.
 */
public class DiscordReactionListener extends ListenerAdapter {

    private final ZDiscord plugin;

    public DiscordReactionListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReactionAdd(MessageReactionAddEvent event) {
        if (event.getUser() == null || event.getUser().isBot()) {
            return;
        }
        if (plugin.getReactionRoleModule() != null) {
            plugin.getReactionRoleModule().onReactionAdd(event);
        }
    }

    @Override
    public void onMessageReactionRemove(MessageReactionRemoveEvent event) {
        if (plugin.getReactionRoleModule() != null) {
            plugin.getReactionRoleModule().onReactionRemove(event);
        }
    }
}

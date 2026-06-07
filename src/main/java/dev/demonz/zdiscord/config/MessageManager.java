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

package dev.demonz.zdiscord.config;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Loads and provides access to the user-facing strings in
 * {@code messages.yml}.
 */
public class MessageManager {

    private final ZDiscord plugin;
    private FileConfiguration messages;
    private File messagesFile;

    public MessageManager(ZDiscord plugin) {
        this.plugin = plugin;
        loadMessages();
    }

    private void loadMessages() {
        messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(defStream));
            messages.setDefaults(defaults);
            messages.options().copyDefaults(true);
        }
    }

    public void reload() {
        loadMessages();
    }

    /**
     * Get a message by key. The {@code %prefix%} placeholder is
     * replaced and Minecraft colour codes are translated.
     */
    public String get(String key) {
        String msg = messages.getString(key, "&cMissing message: " + key);
        String prefix = messages.getString("prefix", "&bZDiscord &8> &7");
        return ChatColor.translateAlternateColorCodes('&', msg.replace("%prefix%", prefix));
    }

    /**
     * Get a message with placeholders replaced. Placeholders are given
     * as alternating {@code key, value} pairs.
     */
    public String get(String key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i + 1 < replacements.length; i += 2) {
            if (replacements[i] == null || replacements[i + 1] == null) {
                continue;
            }
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    /**
     * Get a message by key without applying the prefix.
     */
    public String getRaw(String key) {
        String msg = messages.getString(key, key);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}

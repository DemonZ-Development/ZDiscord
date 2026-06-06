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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Maps Discord reactions to Discord roles and (optionally) to
 * in-game permissions granted through LuckPerms.
 */
public class ReactionRoleModule {

    private final ZDiscord plugin;
    private final File dataFile;
    private FileConfiguration data;
    private final Map<String, Map<String, RoleMapping>> mappings = new HashMap<>();

    public ReactionRoleModule(ZDiscord plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "reaction_roles.yml");
    }

    public void init() {
        loadData();
    }

    private void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create reaction roles file: "
                        + e.getMessage());
                return;
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        mappings.clear();

        if (data.getConfigurationSection("messages") == null) {
            return;
        }
        for (String messageId : data.getConfigurationSection("messages").getKeys(false)) {
            Map<String, RoleMapping> emojiMap = new HashMap<>();
            for (String emoji : data.getConfigurationSection("messages." + messageId).getKeys(false)) {
                String roleId = data.getString("messages." + messageId + "." + emoji + ".role-id");
                String permission = data.getString(
                        "messages." + messageId + "." + emoji + ".permission", "");
                emojiMap.put(emoji, new RoleMapping(roleId, permission));
            }
            mappings.put(messageId, emojiMap);
        }
    }

    public void addMapping(String messageId, String emoji, String roleId, String permission) {
        mappings.computeIfAbsent(messageId, k -> new HashMap<>())
                .put(emoji, new RoleMapping(roleId, permission));
        saveData();
    }

    public void onReactionAdd(MessageReactionAddEvent event) {
        RoleMapping mapping = lookup(event.getMessageId(), event.getReaction().getEmoji().getAsReactionCode());
        if (mapping == null) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (guild == null || member == null) {
            return;
        }
        applyRole(guild, member, mapping.roleId, true);
        applyPermission(event.getUserId(), mapping.permission, true);
    }

    public void onReactionRemove(MessageReactionRemoveEvent event) {
        RoleMapping mapping = lookup(event.getMessageId(), event.getReaction().getEmoji().getAsReactionCode());
        if (mapping == null) {
            return;
        }
        Guild guild = event.getGuild();
        Member member = guild != null ? guild.getMemberById(event.getUserId()) : null;
        if (guild == null || member == null) {
            return;
        }
        applyRole(guild, member, mapping.roleId, false);
        applyPermission(event.getUserId(), mapping.permission, false);
    }

    private RoleMapping lookup(String messageId, String emoji) {
        Map<String, RoleMapping> emojiMap = mappings.get(messageId);
        return emojiMap != null ? emojiMap.get(emoji) : null;
    }

    private void applyRole(Guild guild, Member member, String roleId, boolean add) {
        if (roleId == null || roleId.isEmpty()) {
            return;
        }
        Role role = guild.getRoleById(roleId);
        if (role == null) {
            return;
        }
        if (add) {
            guild.addRoleToMember(member, role).queue(
                    success -> plugin.debug("Added role " + role.getName()
                            + " to " + member.getEffectiveName()),
                    error -> plugin.debug("Failed to add role: " + error.getMessage()));
        } else {
            guild.removeRoleFromMember(member, role).queue();
        }
    }

    private void applyPermission(String discordId, String permission, boolean grant) {
        if (permission == null || permission.isEmpty() || plugin.getLinkModule() == null) {
            return;
        }
        UUID playerUUID = plugin.getLinkModule().getPlayerUUID(discordId);
        if (playerUUID == null) {
            return;
        }
        String action = grant ? "set" : "unset";
        String value = grant ? " true" : "";
        String command = "lp user " + playerUUID + " permission " + action + " " + permission + value;
        plugin.getPlatformAdapter().runSync(
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private void saveData() {
        for (Map.Entry<String, Map<String, RoleMapping>> msgEntry : mappings.entrySet()) {
            for (Map.Entry<String, RoleMapping> emojiEntry : msgEntry.getValue().entrySet()) {
                String path = "messages." + msgEntry.getKey() + "." + emojiEntry.getKey();
                data.set(path + ".role-id", emojiEntry.getValue().roleId);
                data.set(path + ".permission", emojiEntry.getValue().permission);
            }
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save reaction roles data: " + e.getMessage());
        }
    }

    public void shutdown() {
        saveData();
    }

    private static final class RoleMapping {
        final String roleId;
        final String permission;

        RoleMapping(String roleId, String permission) {
            this.roleId = roleId;
            this.permission = permission != null ? permission : "";
        }
    }
}

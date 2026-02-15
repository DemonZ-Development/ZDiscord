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
 * Reaction role module.
 * Maps Discord emoji reactions to Discord roles and in-game permissions.
 */
public class ReactionRoleModule {

    private final ZDiscord plugin;
    private final File dataFile;
    private FileConfiguration data;

    // messageId → (emoji → RoleMapping)
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
                plugin.getLogger().warning("Failed to create reaction roles file: " + e.getMessage());
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (data.getConfigurationSection("messages") != null) {
            for (String messageId : data.getConfigurationSection("messages").getKeys(false)) {
                Map<String, RoleMapping> emojiMap = new HashMap<>();
                for (String emoji : data.getConfigurationSection("messages." + messageId).getKeys(false)) {
                    String roleId = data.getString("messages." + messageId + "." + emoji + ".role-id");
                    String permission = data.getString("messages." + messageId + "." + emoji + ".permission", "");
                    emojiMap.put(emoji, new RoleMapping(roleId, permission));
                }
                mappings.put(messageId, emojiMap);
            }
        }
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

    /**
     * Add a reaction role mapping.
     */
    public void addMapping(String messageId, String emoji, String roleId, String permission) {
        mappings.computeIfAbsent(messageId, k -> new HashMap<>())
                .put(emoji, new RoleMapping(roleId, permission));
        saveData();
    }

    /**
     * Handle reaction add event.
     */
    public void onReactionAdd(MessageReactionAddEvent event) {
        String messageId = event.getMessageId();
        if (!mappings.containsKey(messageId))
            return;

        String emoji = event.getReaction().getEmoji().getAsReactionCode();
        RoleMapping mapping = mappings.get(messageId).get(emoji);
        if (mapping == null)
            return;

        Guild guild = event.getGuild();
        Member member = event.getMember();
        if (member == null)
            return;

        // Add Discord role
        Role role = guild.getRoleById(mapping.roleId);
        if (role != null) {
            guild.addRoleToMember(member, role).queue(
                    success -> plugin.debug("Added role " + role.getName() + " to " + member.getEffectiveName()),
                    error -> plugin.debug("Failed to add role: " + error.getMessage()));
        }

        // Apply in-game permission if linked
        if (!mapping.permission.isEmpty() && plugin.getLinkModule() != null) {
            UUID playerUUID = plugin.getLinkModule().getPlayerUUID(event.getUserId());
            if (playerUUID != null) {
                plugin.getPlatformAdapter().runSync(() -> {
                    // Execute permission command (works with LuckPerms, etc.)
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "lp user " + playerUUID + " permission set " + mapping.permission + " true");
                });
            }
        }
    }

    /**
     * Handle reaction remove event.
     */
    public void onReactionRemove(MessageReactionRemoveEvent event) {
        String messageId = event.getMessageId();
        if (!mappings.containsKey(messageId))
            return;

        String emoji = event.getReaction().getEmoji().getAsReactionCode();
        RoleMapping mapping = mappings.get(messageId).get(emoji);
        if (mapping == null)
            return;

        Guild guild = event.getGuild();
        Member member = guild.getMemberById(event.getUserId());
        if (member == null)
            return;

        // Remove Discord role
        Role role = guild.getRoleById(mapping.roleId);
        if (role != null) {
            guild.removeRoleFromMember(member, role).queue();
        }

        // Remove in-game permission if linked
        if (!mapping.permission.isEmpty() && plugin.getLinkModule() != null) {
            UUID playerUUID = plugin.getLinkModule().getPlayerUUID(event.getUserId());
            if (playerUUID != null) {
                plugin.getPlatformAdapter().runSync(() -> {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                            "lp user " + playerUUID + " permission unset " + mapping.permission);
                });
            }
        }
    }

    public void shutdown() {
        saveData();
    }

    /**
     * Simple data class for role mappings.
     */
    private static class RoleMapping {
        final String roleId;
        final String permission;

        RoleMapping(String roleId, String permission) {
            this.roleId = roleId;
            this.permission = permission != null ? permission : "";
        }
    }
}

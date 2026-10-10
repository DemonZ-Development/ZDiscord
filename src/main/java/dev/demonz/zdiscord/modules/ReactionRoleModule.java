package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.ZDiscord;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent;
import net.dv8tion.jda.api.events.message.react.GenericMessageReactionEvent;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import org.bukkit.configuration.ConfigurationSection;
import net.dv8tion.jda.api.entities.emoji.Emoji;

public class ReactionRoleModule {

    private static final Pattern PERMISSION_PATTERN = Pattern.compile("^[a-z0-9._-]+$");

    private final ZDiscord plugin;
    private final File dataFile;
    private final Map<String, Map<String, RoleMapping>> storedMappings = new ConcurrentHashMap<>();
    private volatile Map<String, Map<String, RoleMapping>> mappings = Map.of();
    private FileConfiguration data;
    private volatile boolean running;

    public ReactionRoleModule(ZDiscord plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "reaction_roles.yml");
    }

    public void init() {
        running = true;
        loadData();
    }

    private synchronized void loadData() {
        if (!dataFile.exists()) {
            try {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Failed to create reaction roles file: "
                        + e.getMessage());
                return;
            }
        }
        data = YamlConfiguration.loadConfiguration(dataFile);
        storedMappings.clear();

        var messages = data.getConfigurationSection("messages");
        if (messages != null) for (String messageId : messages.getKeys(false)) {
            Map<String, RoleMapping> emojiMap = new ConcurrentHashMap<>();
            var messageSection = messages.getConfigurationSection(messageId);
            if (messageSection == null) continue;
            for (String emoji : messageSection.getKeys(false)) {
                String path = "messages." + messageId + "." + emoji;
                String roleId = data.getString(path + ".role-id");
                String permission = data.getString(path + ".permission", "");
                if (permission != null && !permission.isEmpty()
                        && !PERMISSION_PATTERN.matcher(permission).matches()) {
                    plugin.getLogger().warning("Skipping reaction-role mapping with invalid "
                            + "permission '" + permission + "' for emoji " + emoji
                            + " on message " + messageId);
                    continue;
                }
                emojiMap.put(emoji, new RoleMapping(roleId, permission));
            }
            storedMappings.put(messageId, emojiMap);
        }
        refreshMappings();
    }

    private void refreshMappings() {
        Map<String, Map<String, RoleMapping>> combined = new HashMap<>();
        storedMappings.forEach((message, entries) -> combined.put(message, new HashMap<>(entries)));
        loadConfiguredMappings(plugin.getConfigManager().getConfig(), plugin.getLogger()::warning)
                .forEach((message, entries) -> combined.computeIfAbsent(message, ignored -> new HashMap<>()).putAll(entries));
        combined.replaceAll((message, entries) -> Map.copyOf(entries));
        mappings = Map.copyOf(combined);
    }

    static Map<String, Map<String, RoleMapping>> loadConfiguredMappings(ConfigurationSection config, Consumer<String> warn) {
        Map<String, Map<String, RoleMapping>> result = new HashMap<>();
        for (Map<?, ?> entry : config.getMapList("reaction-roles.mappings")) {
            String message = string(entry, "message-id");
            String role = string(entry, "role-id");
            String emoji = string(entry, "emoji");
            String permission = string(entry, "minecraft-permission");
            if (permission.isEmpty()) permission = string(entry, "permission");
            if (!TicketModule.isUsableSnowflake(message) || !TicketModule.isUsableSnowflake(role)
                    || emoji.isBlank() || (!permission.isEmpty() && !PERMISSION_PATTERN.matcher(permission).matches())) {
                warn.accept("Skipping invalid reaction-roles.mappings entry for message " + message);
                continue;
            }
            try { emoji = Emoji.fromFormatted(emoji).getAsReactionCode(); }
            catch (IllegalArgumentException invalid) {
                warn.accept("Skipping invalid reaction-role emoji for message " + message);
                continue;
            }
            result.computeIfAbsent(message, ignored -> new HashMap<>()).put(emoji, new RoleMapping(role, permission));
        }
        return result;
    }

    private static String string(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value == null ? "" : value.toString().trim();
    }

    public synchronized void addMapping(String messageId, String emoji, String roleId, String permission) {
        if (permission != null && !permission.isEmpty()
                && !PERMISSION_PATTERN.matcher(permission).matches()) {
            plugin.getLogger().warning("Invalid permission format rejected: " + permission);
            return;
        }
        storedMappings.computeIfAbsent(messageId, k -> new ConcurrentHashMap<>())
                .put(emoji, new RoleMapping(roleId, permission));
        refreshMappings();
        saveData();
    }

    public void onReactionAdd(MessageReactionAddEvent event) {
        handleReaction(event, true);
    }

    public void onReactionRemove(MessageReactionRemoveEvent event) {
        handleReaction(event, false);
    }

    private void handleReaction(GenericMessageReactionEvent event, boolean add) {
        if (!running || !event.isFromGuild() || !event.getGuild().getId().equals(
                plugin.getConfigManager().getString("bot.guild-id"))) return;
        String emoji = event.getEmoji().getAsReactionCode();
        RoleMapping mapping = lookup(event.getMessageId(), emoji);
        if (mapping == null) return;
        Consumer<Member> apply = member -> {
            if (!running || member.getUser().isBot() || mapping != lookup(event.getMessageId(), emoji)) return;
            try {
                applyRole(event.getGuild(), member, mapping.roleId, add);
                applyPermission(event.getUserId(), mapping.permission, add);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("Reaction-role update failed: " + error.getMessage());
            }
        };
        if (event.getMember() != null) apply.accept(event.getMember());
        else event.retrieveMember().queue(apply,
                error -> plugin.debug("Could not retrieve reaction-role member: " + error.getMessage()));
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
            guild.removeRoleFromMember(member, role).queue(null,
                    error -> plugin.debug("Failed to remove role: " + error.getMessage()));
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
        String command = "lp user " + playerUUID + " permission " + action
                + " " + permission + value;
        plugin.getPlatformAdapter().runSync(
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command));
    }

    private synchronized void saveData() {
        if (data == null) return;
        data.set("messages", null);
        for (Map.Entry<String, Map<String, RoleMapping>> msgEntry : storedMappings.entrySet()) {
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
        running = false;
        saveData();
    }

    public void reload() { loadData(); }

    static final class RoleMapping {
        final String roleId;
        final String permission;

        RoleMapping(String roleId, String permission) {
            this.roleId = roleId;
            this.permission = permission != null ? permission : "";
        }
    }
}

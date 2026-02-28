package dev.demonz.zdiscord.config;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Manages user-facing messages with placeholder support.
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

        // Merge defaults
        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defStream));
            messages.setDefaults(defConfig);
        }
    }

    public void reload() {
        loadMessages();
    }

    /**
     * Get a message with color codes translated and prefix applied.
     */
    public String get(String key) {
        String msg = messages.getString(key, "&cMissing message: " + key);
        String prefix = messages.getString("prefix", "&b&lZ&3Discord &8» &7");
        msg = msg.replace("%prefix%", prefix);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    /**
     * Get a message with placeholders replaced.
     */
    public String get(String key, String... replacements) {
        String msg = get(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            msg = msg.replace(replacements[i], replacements[i + 1]);
        }
        return msg;
    }

    /**
     * Get a raw message without prefix processing.
     */
    public String getRaw(String key) {
        String msg = messages.getString(key, key);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}

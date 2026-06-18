package dev.demonz.zdiscord.config;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;


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


    public String get(String key) {
        String msg = messages.getString(key, "&cMissing message: " + key);
        String prefix = messages.getString("prefix", "&bZDiscord &8> &7");
        return ChatColor.translateAlternateColorCodes('&', msg.replace("%prefix%", prefix));
    }


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


    public String getRaw(String key) {
        String msg = messages.getString(key, key);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }
}

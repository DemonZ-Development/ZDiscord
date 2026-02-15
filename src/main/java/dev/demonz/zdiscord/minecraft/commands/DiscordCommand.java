package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

/**
 * /discord command - Shows the Discord invite link with a polished UI.
 */
public class DiscordCommand implements CommandExecutor {

    private final ZDiscord plugin;

    public DiscordCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String link = plugin.getConfigManager().getString("misc.invite-link", "https://discord.gg/yourserver");

        sender.sendMessage("");
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("  §b§l⚡ §3§lZDiscord §8— §7Join Our Community");
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("");
        sender.sendMessage("  §b❯ §f§n" + link);
        sender.sendMessage("");
        sender.sendMessage("  §7Click the link above to join our Discord!");
        sender.sendMessage("");
        sender.sendMessage("§8§m                                              ");
        sender.sendMessage("");

        return true;
    }
}

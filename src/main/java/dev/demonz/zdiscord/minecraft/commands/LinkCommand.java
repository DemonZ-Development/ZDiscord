package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {

    private final ZDiscord plugin;

    public LinkCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageManager().get("player-only"));
            return true;
        }
        if (!sender.hasPermission("zdiscord.link")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        if (plugin.getLinkModule() == null) {
            sender.sendMessage("Account linking is disabled in config.yml.");
            return true;
        }

        String code = plugin.getLinkModule().generateCode(player);
        if (code == null) return true;
        sender.sendMessage(plugin.getMessageManager().get("link-code-generated", "%code%", code));
        return true;
    }
}

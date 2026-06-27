package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ConfessCommand implements CommandExecutor {

    private final ZDiscord plugin;

    public ConfessCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessageManager().get("player-only"));
            return true;
        }
        if (!sender.hasPermission("zdiscord.confess")) {
            sender.sendMessage(plugin.getMessageManager().get("no-permission"));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("Usage: /" + label + " <message>");
            return true;
        }

        plugin.getConfessionModule().postFromMinecraft(
                (Player) sender, String.join(" ", args));
        return true;
    }
}

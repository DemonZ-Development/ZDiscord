package dev.demonz.zdiscord.minecraft.commands;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;


public class DiscordCommand implements CommandExecutor {

    private final ZDiscord plugin;

    public DiscordCommand(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String link = plugin.getConfigManager().getString(
                "misc.invite-link", "https://discord.gg/yourserver");
        sender.sendMessage(plugin.getMessageManager().get(
                "discord-link", "%link%", link));
        return true;
    }
}

package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class PaperStaffChatListener implements Listener {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    private final ZDiscord plugin;

    public PaperStaffChatListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (plugin.getStaffChatModule() == null) {
            return;
        }
        if (!plugin.getStaffChatModule().isToggled(event.getPlayer().getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        String message = PLAIN.serialize(event.message());
        String finalMessage = message;
        plugin.getPlatformAdapter().runSync(() -> {
            plugin.getStaffChatModule().broadcastToStaff(event.getPlayer(), finalMessage);
            plugin.getStaffChatModule().sendToDiscord(event.getPlayer(), finalMessage);
        });
    }
}

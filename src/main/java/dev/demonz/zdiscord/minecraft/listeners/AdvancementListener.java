package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import dev.demonz.zdiscord.util.ColorUtil;
import dev.demonz.zdiscord.util.HeadUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.advancement.Advancement;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

import java.time.Instant;


public class AdvancementListener implements Listener {


    private final ZDiscord plugin;

    public AdvancementListener(ZDiscord plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getBotManager().isConnected()) {
            return;
        }
        if (!plugin.getConfigManager().getBoolean("events.advancement.enabled", true)) {
            return;
        }
        boolean showRarity = plugin.getConfigManager()
                .getBoolean("events.advancement.show-rarity", true);

        Advancement advancement = event.getAdvancement();
        String key = advancement.getKey().getKey();
        if (key.startsWith("recipes/")) {
            return;
        }

        Player player = event.getPlayer();
        String advancementName = formatAdvancementName(key);










        String finalKey = key;
        String finalName = advancementName;
        plugin.getPlatformAdapter().runAsync(() -> {
            boolean genuinelyNew = plugin.getStorageManager()
                    .recordAdvancementUnlockIfNew(player.getUniqueId(), finalKey);
            int unlockers = plugin.getStorageManager()
                    .getAdvancementUnlockerCount(finalKey);
            int active = plugin.getStorageManager()
                    .getAdvancementActivePlayerCount();
            double rarityThreshold = plugin.getConfigManager()
                    .getDouble("events.advancement.rarity-threshold", 0.25);
            boolean serverFirst = showRarity && unlockers <= 1;
            boolean rare = showRarity && active >= 5
                    && ((double) unlockers / (double) active) < rarityThreshold;
            plugin.getPlatformAdapter().runForEntity(player,
                    () -> sendEmbed(player, finalName, unlockers, active,
                            serverFirst, rare, genuinelyNew));
        });
    }

    private void sendEmbed(Player player, String advancementName,
                           int unlockers, int active, boolean serverFirst,
                           boolean rare, boolean genuinelyNew) {
        TextChannel channel = plugin.getBotManager().getTextChannel("channels.achievements");
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.events");
        }
        if (channel == null) {
            channel = plugin.getBotManager().getTextChannel("channels.chat");
        }
        if (channel == null) {
            return;
        }

        String colorHex = plugin.getConfigManager()
                .getString("events.advancement.color", "#F1C40F");
        String avatarFormat = plugin.getConfigManager()
                .getString("chat.avatar-url",
                        "https://crafatar.com/avatars/%uuid%?overlay=true");
        String avatarUrl = HeadUtil.resolve(avatarFormat,
                player.getUniqueId(), player.getName());

        int colorInt;
        try {
            colorInt = ColorUtil.parseHex(colorHex).getRGB() & 0xFFFFFF;
        } catch (Exception e) {
            colorInt = 0xF1C40F;
        }


        if (rare) {
            colorInt = 0xF1C40F;
        } else if (serverFirst) {
            colorInt = 0x2ECC71;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName() + " earned an advancement", null, avatarUrl)
                .setTitle(":trophy: " + advancementName)
                .setColor(colorInt)
                .setTimestamp(Instant.now());

        if (serverFirst) {
            embed.addField(":1st_place_medal: Server First",
                    "**" + player.getName() + "** is the first player "
                            + "to unlock this advancement on the server.",
                    false);
        } else if (rare && active > 0) {
            int pct = (int) Math.round((unlockers * 100.0) / active);
            embed.addField(":sparkles: Rare achievement",
                    "Only **" + pct + "%** of players who've logged "
                            + "an advancement on this server have unlocked this one "
                            + "(" + unlockers + " out of " + active + ").",
                    false);
        }




        if (genuinelyNew && active > 0 && !rare && !serverFirst) {
            int pct = (int) Math.round((unlockers * 100.0) / active);
            embed.addField("Unlocked by", pct + "% of players ("
                    + unlockers + " of " + active + ")", true);
        }

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> { },
                error -> plugin.debug("Failed to send advancement embed: "
                        + error.getMessage()));
    }

    private String formatAdvancementName(String key) {
        if (key.contains("/")) {
            key = key.substring(key.lastIndexOf('/') + 1);
        }
        String[] words = key.split("_");
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                sb.append(word.substring(1));
            }
        }
        return sb.toString();
    }
}

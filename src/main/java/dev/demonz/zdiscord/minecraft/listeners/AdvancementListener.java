/*
 * Copyright 2026 DemonZ Development
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * Forwards advancement completions to a Discord channel. Recipe
 * advancements are skipped. Each announcement is enriched with
 * a rarity badge ("first of the day" or "rare — only N% of
 * players have this") computed from the persistent
 * advancement-unlock ledger in storage.
 */
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

        // Persist the unlock into the ledger and pull the rarity
        // stats in the same async hop. Storage I/O is on the hot
        // path, so we defer the embed build by one tick.
        //
        // Guard against duplicate events: the same advancement
        // can fire twice if the plugin is reloaded mid-session
        // or a player re-joins immediately.  recordAdvancement-
        // Unlock is idempotent, but we also skip the embed if
        // the player already had this advancement in storage.
        String finalKey = key;
        String finalName = advancementName;
        plugin.getPlatformAdapter().runAsync(() -> {
            int existingCount = plugin.getStorageManager()
                    .getPlayerAdvancementCount(player.getUniqueId());
            plugin.getStorageManager().recordAdvancementUnlock(
                    player.getUniqueId(), finalKey);
            // Re-read: the count may have increased by 1 if the
            // unlock was truly new.
            int newCount = plugin.getStorageManager()
                    .getPlayerAdvancementCount(player.getUniqueId());
            boolean genuinelyNew = newCount > existingCount;
            int unlockers = plugin.getStorageManager()
                    .getAdvancementUnlockerCount(finalKey);
            int active = plugin.getStorageManager()
                    .getAdvancementActivePlayerCount();
            double rarityThreshold = plugin.getConfigManager()
                    .getDouble("events.advancement.rarity-threshold", 0.25);
            boolean firstOfDay = showRarity && unlockers <= 1;
            boolean rare = showRarity && active >= 5
                    && ((double) unlockers / (double) active) < rarityThreshold;
            plugin.getPlatformAdapter().runForEntity(player,
                    () -> sendEmbed(player, finalName, unlockers, active,
                            firstOfDay, rare, genuinelyNew));
        });
    }

    private void sendEmbed(Player player, String advancementName,
                           int unlockers, int active, boolean firstOfDay,
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

        int colorInt = ColorUtil.parseHex(colorHex).getRGB() & 0xFFFFFF;
        // Rare overrides the configured colour with a saturated
        // gold; first-of-day is allowed to keep the default.
        if (rare) {
            colorInt = 0xF1C40F;
        } else if (firstOfDay) {
            colorInt = 0x2ECC71;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(player.getName() + " earned an advancement", null, avatarUrl)
                .setTitle(":trophy: " + advancementName)
                .setColor(colorInt)
                .setTimestamp(Instant.now());

        if (firstOfDay) {
            embed.addField(":1st_place_medal: First of the day",
                    "**" + player.getName() + "** is the first player "
                            + "to unlock this advancement in the last 24 hours.",
                    false);
        } else if (rare && active > 0) {
            int pct = (int) Math.round((unlockers * 100.0) / active);
            embed.addField(":sparkles: Rare achievement",
                    "Only **" + pct + "%** of players who've logged "
                            + "an advancement on this server have unlocked this one "
                            + "(" + unlockers + " out of " + active + ").",
                    false);
        }

        // Only show the generic "Unlocked by" field for genuinely
        // new unlocks.  A duplicate event (e.g. from a reload)
        // would confuse players if it posted a redundant embed.
        if (genuinelyNew && active > 0 && !rare && !firstOfDay) {
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

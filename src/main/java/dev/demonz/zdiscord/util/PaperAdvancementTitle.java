package dev.demonz.zdiscord.util;

import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;

public final class PaperAdvancementTitle {
    private PaperAdvancementTitle() { }

    @SuppressWarnings({"deprecation", "removal"})
    public static String get(Advancement advancement) {
        var display = advancement.getDisplay();
        return display == null ? null : Bukkit.getUnsafe().plainTextSerializer().serialize(display.title());
    }
}

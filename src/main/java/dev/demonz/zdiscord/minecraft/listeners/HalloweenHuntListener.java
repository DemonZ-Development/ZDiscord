package dev.demonz.zdiscord.minecraft.listeners;

import dev.demonz.zdiscord.ZDiscord;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.persistence.PersistentDataType;

public final class HalloweenHuntListener implements Listener {
    private final ZDiscord plugin;
    private final NamespacedKey spawnerKey;

    public HalloweenHuntListener(ZDiscord plugin) {
        this.plugin = plugin;
        spawnerKey = new NamespacedKey(plugin, "halloween-spawner-mob");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        var hunt = plugin.getHalloweenModule();
        if (hunt != null && !(event.getEntity() instanceof Player)) hunt.recordKill(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (event.getEntity() instanceof LivingEntity mob) {

            mob.getPersistentDataContainer().set(spawnerKey, PersistentDataType.BYTE, (byte) 1);
        }
    }
}

package dev.demonz.zdiscord.api.events;

import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class ZDiscordStatUpdateEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUUID;
    private final String stat;
    private final long oldValue;
    private final long newValue;
    private boolean cancelled;

    public ZDiscordStatUpdateEvent(UUID playerUUID, String stat,
                                   long oldValue, long newValue) {
        this(playerUUID, stat, oldValue, newValue, false);
    }

    public ZDiscordStatUpdateEvent(UUID playerUUID, String stat,
                                   long oldValue, long newValue,
                                   boolean async) {
        super(async);
        this.playerUUID = playerUUID;
        this.stat = stat;
        this.oldValue = oldValue;
        this.newValue = newValue;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getStat() { return stat; }
    public long getOldValue() { return oldValue; }
    public long getNewValue() { return newValue; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

package dev.demonz.zdiscord.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class ZDiscordPlayerLinkEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUUID;
    private final String discordId;
    private final boolean linked;

    public ZDiscordPlayerLinkEvent(UUID playerUUID, String discordId, boolean linked) {
        super(true);
        this.playerUUID = playerUUID;
        this.discordId = discordId;
        this.linked = linked;
    }


    public UUID getPlayerUUID() { return playerUUID; }


    public String getDiscordId() { return discordId; }


    public boolean isLinked() { return linked; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

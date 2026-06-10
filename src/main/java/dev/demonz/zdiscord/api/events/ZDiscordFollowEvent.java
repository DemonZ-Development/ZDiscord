package dev.demonz.zdiscord.api.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.UUID;

public class ZDiscordFollowEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID targetPlayerUUID;
    private final String followerDiscordId;
    private final boolean followed;

    public ZDiscordFollowEvent(UUID targetPlayerUUID, String followerDiscordId,
                               boolean followed) {
        super(true);
        this.targetPlayerUUID = targetPlayerUUID;
        this.followerDiscordId = followerDiscordId;
        this.followed = followed;
    }


    public UUID getTargetPlayerUUID() { return targetPlayerUUID; }


    public String getFollowerDiscordId() { return followerDiscordId; }


    public boolean isFollowed() { return followed; }

    @Override
    public HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}

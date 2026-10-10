package dev.demonz.zdiscord.modules;

import dev.demonz.zdiscord.storage.StorageManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

final class AccountLinks {
    static final long EXPIRY_MILLIS = 5 * 60 * 1000L;
    private final StorageManager storage;
    private final LongSupplier clock;
    private final Map<UUID, String> accounts = new HashMap<>();
    private final Map<String, UUID> discordAccounts = new HashMap<>();
    private final Map<String, Pending> codes = new HashMap<>();
    private final Map<UUID, String> playerCodes = new HashMap<>();

    record Change(UUID player, UUID previousPlayer) { }
    private record Pending(UUID player, long created) { }

    AccountLinks(StorageManager storage) {
        this(storage, System::currentTimeMillis);
    }

    AccountLinks(StorageManager storage, LongSupplier clock) {
        this.storage = storage;
        this.clock = clock;
        storage.loadLinks().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String discord = entry.getValue();
            if (discord == null || discord.isBlank()) return;
            UUID previous = discordAccounts.put(discord, entry.getKey());
            if (previous != null) {
                accounts.remove(previous);
                storage.removeLink(previous);
            }
            accounts.put(entry.getKey(), discord);
        });
    }

    synchronized String code(UUID player, boolean reuse) {
        if (accounts.containsKey(player)) return null;
        expire();
        String previous = playerCodes.get(player);
        if (reuse && previous != null) return previous;
        invalidate(player);
        String code;
        do {
            code = UUID.randomUUID().toString().substring(0, 6).toUpperCase(java.util.Locale.ROOT);
        } while (codes.containsKey(code));
        codes.put(code, new Pending(player, clock.getAsLong()));
        playerCodes.put(player, code);
        return code;
    }

    synchronized Change redeem(String discord, String code) {
        if (discord == null || discord.isBlank() || code == null) return null;
        Pending pending = codes.remove(code.trim().toUpperCase(java.util.Locale.ROOT));
        if (pending == null) return null;
        playerCodes.remove(pending.player());
        if (clock.getAsLong() - pending.created() >= EXPIRY_MILLIS
                || accounts.containsKey(pending.player())) return null;

        UUID previous = discordAccounts.put(discord, pending.player());
        if (previous != null && discord.equals(accounts.get(previous))) {
            accounts.remove(previous);
            invalidate(previous);
            storage.removeLink(previous);
        }
        accounts.put(pending.player(), discord);

        storage.saveLink(pending.player(), discord);
        return new Change(pending.player(), previous);
    }

    synchronized String unlink(UUID player) {
        invalidate(player);
        String discord = accounts.remove(player);
        if (discord != null) {
            discordAccounts.remove(discord, player);
            storage.removeLink(player);
        }
        return discord;
    }

    synchronized String discord(UUID player) { return accounts.get(player); }
    synchronized UUID player(String discord) { return discordAccounts.get(discord); }
    synchronized int size() { return accounts.size(); }

    synchronized void expire() {
        long now = clock.getAsLong();
        codes.entrySet().removeIf(entry -> {
            if (now - entry.getValue().created() < EXPIRY_MILLIS) return false;
            playerCodes.remove(entry.getValue().player(), entry.getKey());
            return true;
        });
    }

    private void invalidate(UUID player) {
        String code = playerCodes.remove(player);
        if (code != null) codes.remove(code);
    }
}

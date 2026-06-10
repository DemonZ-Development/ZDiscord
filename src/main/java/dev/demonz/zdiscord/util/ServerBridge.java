package dev.demonz.zdiscord.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;


public final class ServerBridge {

    private ServerBridge() {
    }


    public interface Backend {
        Collection<? extends Player> getOnlinePlayers();
        int getMaxPlayers();
        double[] getTPS();
    }


    public static final class BukkitBackend implements Backend {
        @Override public Collection<? extends Player> getOnlinePlayers() { return Bukkit.getOnlinePlayers(); }
        @Override public int getMaxPlayers() { return Bukkit.getMaxPlayers(); }
        @Override public double[] getTPS() { return Bukkit.getTPS(); }
    }


    public static class StubBackend implements Backend {
        protected int onlineCount = 0;
        protected int maxPlayers = 20;
        protected double[] tps = { 20.0, 20.0, 20.0 };

        public StubBackend withMaxPlayers(int max) { this.maxPlayers = max; return this; }
        public StubBackend withTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        @Override public Collection<? extends Player> getOnlinePlayers() {
            int n = onlineCount;
            if (n <= 0) {
                return Collections.emptyList();
            }
            List<Player> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(null);
            }
            return Collections.unmodifiableList(list);
        }
        @Override public int getMaxPlayers() { return maxPlayers; }
        @Override public double[] getTPS() { return tps.clone(); }
    }

    private static volatile Backend backend = new BukkitBackend();


    public static void setBackend(Backend b) {
        backend = b == null ? new BukkitBackend() : b;
    }


    public static void resetBackend() {
        backend = new BukkitBackend();
    }

    public static Collection<? extends Player> onlinePlayers() {
        Backend b = backend;
        return b == null ? Collections.emptyList() : b.getOnlinePlayers();
    }

    public static int maxPlayers() {
        Backend b = backend;
        return b == null ? 0 : b.getMaxPlayers();
    }

    public static double[] tps() {
        Backend b = backend;
        return b == null ? new double[] { 20.0, 20.0, 20.0 } : b.getTPS();
    }
}

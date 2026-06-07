/*
 * Copyright 2024 DemonZ Development
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

package dev.demonz.zdiscord.util;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thin indirection over the static {@code Bukkit.*} server accessors
 * the plugin uses, so tests can swap in a stub without having to
 * install a fake {@code Server} on {@code Bukkit.server}.
 *
 * <p>Why not just set {@code Bukkit.server} like MockBukkit does?
 * Two reasons:</p>
 * <ul>
 *   <li>The Bukkit {@code Server} interface has hundreds of methods,
 *       some of which return types whose static initializers call
 *       {@code Class.forName} on CraftBukkit NMS classes that are
 *       not on the test classpath. Generating a
 *       {@code Proxy.newProxyInstance(Server.class)} walks every
 *       method and triggers that static-init chain, which throws
 *       before the proxy is even usable.</li>
 *   <li>Implementing the entire {@code Server} interface inline
 *       would mean writing ~100 stub methods.</li>
 * </ul>
 *
 * <p>This bridge exposes the four methods the plugin actually calls
 * ({@code getOnlinePlayers}, {@code getMaxPlayers}, {@code getTPS},
 * {@code getWorld}) and lets tests replace the implementation in
 * {@code @BeforeEach}.</p>
 */
public final class ServerBridge {

    private ServerBridge() {
    }

    /**
     * The contract every backend must satisfy. The default backend
     * is a {@link BukkitBackend} that delegates to the static
     * {@code Bukkit.*} methods. Tests install a stub backend in
     * {@code @BeforeEach} and call {@link #setBackend(Backend)} with
     * a null value (or use {@link #resetBackend()}) in
     * {@code @AfterEach}.
     */
    public interface Backend {
        Collection<? extends Player> getOnlinePlayers();
        int getMaxPlayers();
        double[] getTPS();
        World getWorld(String name);
    }

    /** Production backend that delegates to the static {@code Bukkit.*} methods. */
    public static final class BukkitBackend implements Backend {
        @Override public Collection<? extends Player> getOnlinePlayers() { return Bukkit.getOnlinePlayers(); }
        @Override public int getMaxPlayers() { return Bukkit.getMaxPlayers(); }
        @Override public double[] getTPS() { return Bukkit.getTPS(); }
        @Override public World getWorld(String name) { return Bukkit.getWorld(name); }
    }

    /**
     * Stub backend for tests. Construct, set values via the builder
     * methods, then install with {@link #setBackend(Backend)}.
     *
     * <p>Thread-safe: the online-players list is a
     * {@link CopyOnWriteArrayList} so the production code can read
     * it concurrently with the test setup.</p>
     */
    public static class StubBackend implements Backend {
        protected final List<Player> onlinePlayers = new CopyOnWriteArrayList<>();
        protected final java.util.Map<String, World> worlds = new java.util.HashMap<>();
        protected int maxPlayers = 20;
        protected double[] tps = { 20.0, 20.0, 20.0 };

        public StubBackend addPlayer(Player p) { onlinePlayers.add(p); return this; }
        public StubBackend addWorld(String name, World w) { worlds.put(name.toLowerCase(), w); return this; }
        public StubBackend withMaxPlayers(int max) { this.maxPlayers = max; return this; }
        public StubBackend withTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        @Override public Collection<? extends Player> getOnlinePlayers() {
            return Collections.unmodifiableList(onlinePlayers);
        }
        @Override public int getMaxPlayers() { return maxPlayers; }
        @Override public double[] getTPS() { return tps.clone(); }
        @Override public World getWorld(String name) {
            return name == null ? null : worlds.get(name.toLowerCase());
        }
    }

    private static volatile Backend backend = new BukkitBackend();

    /**
     * Replace the active backend. Intended for tests; the
     * production code does not call this.
     */
    public static void setBackend(Backend b) {
        backend = b == null ? new BukkitBackend() : b;
    }

    /** Reset to the production backend. */
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

    public static World getWorld(String name) {
        Backend b = backend;
        return b == null ? null : b.getWorld(name);
    }
}

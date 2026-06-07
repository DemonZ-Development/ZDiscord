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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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
 * <p>This bridge exposes the methods the plugin actually calls
 * ({@code getOnlinePlayers}, {@code getMaxPlayers}, {@code getTPS})
 * and lets tests replace the implementation in
 * {@code @BeforeEach}. The {@code Player}/{@code World} objects the
 * production backend would surface are deliberately <em>not</em>
 * part of the contract — see {@link StubBackend} for how tests fake
 * them without constructing full instances.</p>
 */
public final class ServerBridge {

    private ServerBridge() {
    }

    /**
     * The contract every backend must satisfy. The default backend
     * is a {@link BukkitBackend} that delegates to the static
     * {@code Bukkit.*} methods. Tests install a stub backend in
     * {@code @BeforeEach} and call {@link #resetBackend()} in
     * {@code @AfterEach}.
     */
    public interface Backend {
        Collection<? extends Player> getOnlinePlayers();
        int getMaxPlayers();
        double[] getTPS();
    }

    /** Production backend that delegates to the static {@code Bukkit.*} methods. */
    public static final class BukkitBackend implements Backend {
        @Override public Collection<? extends Player> getOnlinePlayers() { return Bukkit.getOnlinePlayers(); }
        @Override public int getMaxPlayers() { return Bukkit.getMaxPlayers(); }
        @Override public double[] getTPS() { return Bukkit.getTPS(); }
    }

    /**
     * Stub backend for tests. Holds <em>counts</em> only, not real
     * {@code Player} or {@code World} instances. The {@code Player}
     * and {@code World} interfaces in paper-api 1.20.4 expose
     * hundreds of abstract methods each — instantiating anonymous
     * classes that satisfy every abstract method is impractical, so
     * {@link #getOnlinePlayers()} returns an unmodifiable list of
     * size {@code onlineCount} whose entries are {@code null}. The
     * production placeholder code only reads
     * {@code .size()}, and {@link StatusEmbedBuilder#capture()} only
     * iterates when {@code onlineCount > 0}; the tests are written
     * to keep {@code onlineCount == 0} on that code path.
     */
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
}

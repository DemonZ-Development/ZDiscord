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

package dev.demonz.zdiscord.testsupport;

import dev.demonz.zdiscord.util.ServerBridge;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test fixture for the {@link ServerBridge} swappable backend.
 *
 * <p>Install a stub backend in {@code @BeforeEach} and call
 * {@link #uninstall()} in {@code @AfterEach}. The fixture exposes
 * fluent builders so tests can add players / worlds and tweak TPS
 * and max-players without writing a custom backend each time.</p>
 *
 * <p>Why not just use MockBukkit? JitPack now requires
 * authentication for unauthenticated downloads, which breaks CI for
 * any project that pulls a JitPack-only artifact. And the
 * alternative of generating a {@code Server} proxy on
 * {@code Bukkit.server} fails because the proxy walk triggers
 * static initializers of types whose {@code Class.forName} calls
 * hit CraftBukkit NMS classes that aren't on the test classpath.
 * The plugin's own code now goes through
 * {@link ServerBridge}, which is a four-method interface we can
 * stub trivially.</p>
 */
public final class BukkitStub {

    private BukkitStub() {
    }

    private static State state;

    /**
     * Install a stub backend. The previous backend is stashed and
     * will be restored by {@link #uninstall()}.
     */
    public static synchronized State install() {
        if (state != null) {
            throw new IllegalStateException("BukkitStub is already installed; call uninstall() first");
        }
        state = new State();
        ServerBridge.setBackend(state);
        return state;
    }

    /** Restore the production backend. */
    public static synchronized void uninstall() {
        if (state == null) {
            return;
        }
        ServerBridge.resetBackend();
        state = null;
    }

    /**
     * Mutable state behind the stub. Holds the online-player list,
     * the world map, and the TPS / max-players values. The
     * fixture-specific methods ({@link #addPlayer}, {@link #addWorld},
     * {@link #withTps}, {@link #withMaxPlayers}) are the test API;
     * the {@link ServerBridge.Backend} methods are what the
     * production code calls.
     */
    public static final class State extends ServerBridge.StubBackend {
        private final AtomicInteger seq = new AtomicInteger();

        public Player addPlayer(String name) {
            World w = worlds.isEmpty() ? null : worlds.values().iterator().next();
            Player p = newPlayer(name, w);
            onlinePlayers.add(p);
            return p;
        }

        public State addWorld(String name) {
            worlds.put(name.toLowerCase(), newWorld(name));
            return this;
        }

        public State withTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        public State withMaxPlayers(int max) {
            this.maxPlayers = max;
            return this;
        }

        private Player newPlayer(String name, World world) {
            UUID id = new UUID(0, seq.incrementAndGet());
            return new Player() {
                @Override public String getName() { return name; }
                @Override public UUID getUniqueId() { return id; }
                @Override public World getWorld() { return world; }
                @Override public Location getLocation() { return new Location(world, 0, 64, 0); }
                @Override public String getDisplayName() { return name; }
                @Override public double getHealth() { return 20.0; }
                @Override public int getFoodLevel() { return 20; }
                @Override public boolean isOnline() { return true; }
                @Override public Spigot spigot() { return new Spigot() {}; }
            };
        }

        private World newWorld(String name) {
            UUID id = UUID.randomUUID();
            return new World() {
                @Override public String getName() { return name; }
                @Override public UUID getUID() { return id; }
                @Override public org.bukkit.generator.ChunkGenerator getGenerator() { return null; }
                @Override public List<Player> getPlayers() { return new ArrayList<>(); }
                @Override
                public Map<org.bukkit.generator.structure.Structure, org.bukkit.util.StructureSearchResult>
                        getStructures(int x, int z,
                                      org.bukkit.generator.structure.Structure structure) {
                    return new HashMap<>();
                }
            };
        }
    }
}

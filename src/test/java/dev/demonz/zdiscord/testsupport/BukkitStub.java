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

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tiny in-house replacement for MockBukkit. Sets {@code Bukkit.server}
 * via reflection to a {@link Server} stub that returns configurable
 * values for {@code getOnlinePlayers}, {@code getMaxPlayers},
 * {@code getTPS}, and {@code getWorld(String)}. Other methods return
 * zero / null / empty; they're not exercised by the plugin's tests.
 *
 * <p>Why not just use MockBukkit? Two reasons:</p>
 * <ul>
 *   <li>JitPack now requires authentication for unauthenticated
 *       downloads, which breaks CI for any project that pulls a
 *       JitPack-only artifact.</li>
 *   <li>Generating a {@link Server} via {@code Proxy.newProxyInstance}
 *       walks every method on the interface and triggers static
 *       initializers of every referenced type. Some of those types
 *       (e.g. {@code org.bukkit.generator.structure.StructureType})
 *       call {@code Class.forName} on NMS classes that are not on
 *       the test classpath, which throws and bricks the proxy. A
 *       concrete stub class avoids that — the JVM only resolves
 *       types we actually invoke.</li>
 * </ul>
 */
public final class BukkitStub {

    private BukkitStub() {
    }

    private static Server previous;
    private static State state;

    /**
     * Install a fresh stub server. The previous value of
     * {@code Bukkit.server} (if any) is stashed and will be restored
     * by {@link #uninstall()}.
     */
    public static synchronized State install() {
        if (state != null) {
            throw new IllegalStateException("BukkitStub is already installed; call uninstall() first");
        }
        try {
            Field f = Bukkit.class.getDeclaredField("server");
            f.setAccessible(true);
            previous = (Server) f.get(null);
            state = new State();
            f.set(null, state);
            return state;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to install BukkitStub", e);
        }
    }

    /**
     * Restore the {@code Bukkit.server} that was in place before
     * {@link #install()} was called.
     */
    public static synchronized void uninstall() {
        if (state == null) {
            return;
        }
        try {
            Field f = Bukkit.class.getDeclaredField("server");
            f.setAccessible(true);
            f.set(null, previous);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to uninstall BukkitStub", e);
        } finally {
            previous = null;
            state = null;
        }
    }

    /**
     * Mutable state behind the stub. Exposed to tests so they can add
     * players, add worlds, and tweak TPS / max-players.
     */
    public static final class State implements Server {
        final Map<String, World> worlds = new HashMap<>();
        final List<Player> onlinePlayers = new ArrayList<>();
        int maxPlayers = 20;
        double[] tps = { 20.0, 20.0, 20.0 };

        public State addWorld(String name) {
            World w = new World() {
                private final UUID id = UUID.randomUUID();
                @Override public String getName() { return name; }
                @Override public UUID getUID() { return id; }
                @Override public org.bukkit.generator.ChunkGenerator getGenerator() { return null; }
                @Override public List<Player> getPlayers() { return Collections.emptyList(); }
            };
            worlds.put(name.toLowerCase(), w);
            return this;
        }

        public Player addPlayer(String name) {
            Player p = newPlayer(name, worlds.isEmpty() ? null : worlds.values().iterator().next());
            onlinePlayers.add(p);
            return p;
        }

        public State setTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        public State setMaxPlayers(int max) {
            this.maxPlayers = max;
            return this;
        }

        // ── The only Server methods the plugin's tests call ────────

        @Override
        public Collection<? extends Player> getOnlinePlayers() {
            return Collections.unmodifiableList(onlinePlayers);
        }

        @Override
        public int getMaxPlayers() {
            return maxPlayers;
        }

        @Override
        public double[] getTPS() {
            return tps.clone();
        }

        @Override
        public World getWorld(String name) {
            return name == null ? null : worlds.get(name.toLowerCase());
        }

        @Override
        public List<World> getWorlds() {
            return new ArrayList<>(worlds.values());
        }

        @Override
        public String getName() { return "BukkitStub"; }
        @Override
        public String getVersion() { return "stub"; }
        @Override
        public String getBukkitVersion() { return "stub"; }
    }

    private static Player newPlayer(String name, World world) {
        PlayerData data = new PlayerData(name, world);
        return new Player() {
            @Override public String getName() { return data.name; }
            @Override public UUID getUniqueId() { return data.id; }
            @Override public World getWorld() { return data.world; }
            @Override public Location getLocation() { return new Location(data.world, 0, 64, 0); }
            @Override public String getDisplayName() { return data.name; }
            @Override public double getHealth() { return 20.0; }
            @Override public int getFoodLevel() { return 20; }
            @Override public boolean isOnline() { return true; }
        };
    }

    /** Backing data for a stubbed player. */
    private static final class PlayerData {
        static final AtomicInteger SEQ = new AtomicInteger();
        final String name;
        final UUID id;
        final World world;

        PlayerData(String name, World world) {
            this.name = name;
            this.id = new UUID(0, SEQ.incrementAndGet());
            this.world = world;
        }
    }
}

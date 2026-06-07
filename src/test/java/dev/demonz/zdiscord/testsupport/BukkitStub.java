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
 * {@code getTPS}, and {@code getWorld(String)}.
 *
 * <p>Why not just use MockBukkit? Two reasons:</p>
 * <ul>
 *   <li>JitPack now requires authentication for unauthenticated
 *       downloads, which breaks CI for any project that pulls a
 *       JitPack-only artifact.</li>
 *   <li>The Bukkit {@code Server}, {@code World}, and {@code Player}
 *       interfaces have hundreds of methods. Implementing them
 *       inline is verbose, and using
 *       {@code Proxy.newProxyInstance} walks every method, which
 *       triggers the static initializer of every referenced type.
 *       One of those types — {@code StructureType} — calls
 *       {@code Class.forName} on CraftBukkit NMS classes that are
 *       not on the test classpath, which throws.</li>
 * </ul>
 *
 * <p>The workaround: make the stub classes <em>abstract</em> (so
 * they only need to override the handful of methods the tests
 * actually call) and instantiate them via
 * {@code sun.misc.Unsafe.allocateInstance}, which skips the
 * constructor and the static-init walk that
 * {@code Proxy.newProxyInstance} does.</p>
 */
public final class BukkitStub {

    private BukkitStub() {
    }

    private static Server previous;
    private static State state;
    private static final sun.misc.Unsafe UNSAFE;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (sun.misc.Unsafe) f.get(null);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

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

            // Allocate abstract ServerState via Unsafe, then
            // initialize its fields via reflection (the constructor
            // is bypassed).
            State s = (State) UNSAFE.allocateInstance(ServerState.class);
            setField(ServerState.class, s, "worlds", new HashMap<String, World>());
            setField(ServerState.class, s, "onlinePlayers", new ArrayList<Player>());
            setField(ServerState.class, s, "maxPlayers", 20);
            setField(ServerState.class, s, "tps", new double[] { 20.0, 20.0, 20.0 });

            state = s;
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

    private static void setField(Class<?> clazz, Object instance, String name, Object value)
            throws ReflectiveOperationException {
        Field f = clazz.getDeclaredField(name);
        f.setAccessible(true);
        f.set(instance, value);
    }

    /**
     * Mutable state behind the stub. Exposed to tests so they can add
     * players, add worlds, and tweak TPS / max-players.
     */
    public static abstract class State implements Server {
        Map<String, World> worlds;
        List<Player> onlinePlayers;
        int maxPlayers;
        double[] tps;

        public State addWorld(String name) {
            StubWorld w = createStubWorld(name);
            worlds.put(name.toLowerCase(), w);
            return this;
        }

        public Player addPlayer(String name) {
            StubPlayer p = createStubPlayer(name,
                    worlds.isEmpty() ? null : worlds.values().iterator().next());
            onlinePlayers.add(p);
            return p;
        }

        public State withTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        public State withMaxPlayers(int max) {
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
    }

    /**
     * Abstract stub. Allocation is via
     * {@link sun.misc.Unsafe#allocateInstance} in {@link #install()};
     * we never call the constructor. The class is declared abstract
     * so the compiler does not require us to implement every method
     * of {@link Server}.
     */
    public static abstract class ServerState extends State {
    }

    /** Allocate a stub world. */
    private static StubWorld createStubWorld(String name) {
        try {
            StubWorld w = (StubWorld) UNSAFE.allocateInstance(StubWorld.class);
            setField(StubWorld.class, w, "name", name);
            setField(StubWorld.class, w, "id", UUID.randomUUID());
            return w;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to allocate StubWorld", e);
        }
    }

    /** Allocate a stub player. */
    private static StubPlayer createStubPlayer(String name, World world) {
        try {
            StubPlayer p = (StubPlayer) UNSAFE.allocateInstance(StubPlayer.class);
            setField(StubPlayer.class, p, "name", name);
            setField(StubPlayer.class, p, "id", new UUID(0, SEQ.incrementAndGet()));
            setField(StubPlayer.class, p, "world", world);
            return p;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to allocate StubPlayer", e);
        }
    }

    private static final AtomicInteger SEQ = new AtomicInteger();

    /**
     * Abstract {@link World} stub. Allocated via
     * {@link sun.misc.Unsafe#allocateInstance} in {@link #createStubWorld};
     * the constructor is never called.
     */
    public static abstract class StubWorld implements World {
        String name;
        UUID id;

        @Override public String getName() { return name; }
        @Override public UUID getUID() { return id; }
    }

    /**
     * Abstract {@link Player} stub. Allocated via
     * {@link sun.misc.Unsafe#allocateInstance} in {@link #createStubPlayer};
     * the constructor is never called.
     */
    public static abstract class StubPlayer implements Player {
        String name;
        UUID id;
        World world;

        @Override public String getName() { return name; }
        @Override public UUID getUniqueId() { return id; }
        @Override public World getWorld() { return world; }
        @Override public org.bukkit.Location getLocation() { return new org.bukkit.Location(world, 0, 64, 0); }
        @Override public String getDisplayName() { return name; }
        @Override public double getHealth() { return 20.0; }
        @Override public int getFoodLevel() { return 20; }
        @Override public boolean isOnline() { return true; }
    }
}

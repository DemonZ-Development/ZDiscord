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
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 * Tiny in-house replacement for MockBukkit. Sets {@code Bukkit.server}
 * via reflection to a {@link Server} proxy that returns configurable
 * stubs for {@code getOnlinePlayers}, {@code getMaxPlayers},
 * {@code getTPS}, and {@code getWorld(String)}. Player instances
 * returned from the server are themselves proxies that only honour
 * the small set of methods exercised by the plugin's tests
 * ({@code getName}, {@code getUniqueId}, {@code getWorld},
 * {@code getLocation}, {@code getDisplayName}, {@code getHealth},
 * {@code getFoodLevel}, {@code isOnline}).
 *
 * <p>Why not just use MockBukkit? Two reasons:</p>
 * <ul>
 *   <li>JitPack now requires authentication for unauthenticated
 *       downloads, which breaks CI for any project that pulls a
 *       JitPack-only artifact.</li>
 *   <li>The plugin's tests only need a handful of {@code Server}
 *       methods. Using dynamic proxies keeps the fixture small and
 *       resilient to Bukkit API additions between Minecraft
 *       versions.</li>
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
            Server proxy = (Server) Proxy.newProxyInstance(
                    BukkitStub.class.getClassLoader(),
                    new Class<?>[] { Server.class },
                    new ServerHandler(state));
            f.set(null, proxy);
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

    /** Mutable state behind the dynamic proxy. */
    public static final class State {
        final Map<String, World> worlds = new HashMap<>();
        final List<Player> onlinePlayers = new ArrayList<>();
        int maxPlayers = 20;
        double[] tps = { 20.0, 20.0, 20.0 };

        public State addWorld(String name) {
            World w = (World) Proxy.newProxyInstance(
                    BukkitStub.class.getClassLoader(),
                    new Class<?>[] { World.class },
                    new WorldHandler(name));
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
    }

    private static Player newPlayer(String name, World world) {
        PlayerData data = new PlayerData(name, world);
        return (Player) Proxy.newProxyInstance(
                BukkitStub.class.getClassLoader(),
                new Class<?>[] { Player.class },
                new PlayerHandler(data));
    }

    /** Backing data for a proxied player. */
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

    private static final class ServerHandler implements InvocationHandler {
        private final State s;

        ServerHandler(State s) { this.s = s; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getOnlinePlayers":
                    return Collections.unmodifiableList(s.onlinePlayers);
                case "getMaxPlayers":
                    return s.maxPlayers;
                case "getTPS":
                    return s.tps.clone();
                case "getWorld":
                    return s.worlds.get(args[0] == null ? "" : ((String) args[0]).toLowerCase());
                case "getWorlds":
                    return new ArrayList<>(s.worlds.values());
                case "hashCode":
                    return 0;
                case "equals":
                    return proxy == args[0];
                case "toString":
                    return "BukkitStub$Server";
                default:
                    return defaultReturn(method);
            }
        }
    }

    private static final class WorldHandler implements InvocationHandler {
        private final String name;
        private final UUID id = UUID.randomUUID();

        WorldHandler(String name) { this.name = name; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getName": return name;
                case "getUID": return id;
                case "getPlayers": return Collections.emptyList();
                case "hashCode": return id.hashCode();
                case "equals": return proxy == args[0];
                case "toString": return "BukkitStub$World[" + name + "]";
                default: return defaultReturn(method);
            }
        }
    }

    private static final class PlayerHandler implements InvocationHandler {
        private final PlayerData d;

        PlayerHandler(PlayerData d) { this.d = d; }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "getName": return d.name;
                case "getUniqueId": return d.id;
                case "getWorld": return d.world;
                case "getLocation":
                    return new Location(d.world, 0, 64, 0);
                case "getDisplayName": return d.name;
                case "getHealth": return 20.0;
                case "getFoodLevel": return 20;
                case "isOnline": return Boolean.TRUE;
                case "hashCode": return d.id.hashCode();
                case "equals": return proxy == args[0];
                case "toString": return "BukkitStub$Player[" + d.name + "]";
                default: return defaultReturn(method);
            }
        }
    }

    /** Default return value for proxy methods we don't implement. */
    private static Object defaultReturn(Method method) {
        Class<?> rt = method.getReturnType();
        if (rt == boolean.class) return Boolean.FALSE;
        if (rt == int.class) return 0;
        if (rt == long.class) return 0L;
        if (rt == double.class) return 0.0;
        if (rt == float.class) return 0.0f;
        if (rt == short.class) return (short) 0;
        if (rt == byte.class) return (byte) 0;
        if (rt == char.class) return (char) 0;
        if (Collection.class.isAssignableFrom(rt) || Map.class.isAssignableFrom(rt)
                || java.util.Optional.class.isAssignableFrom(rt)) {
            return Collections.emptyList();
        }
        if (rt == String.class) return "stub";
        if (rt == Logger.class) return Logger.getLogger("BukkitStub");
        if (method.getName().startsWith("is") || method.getName().startsWith("has")) {
            return Boolean.FALSE;
        }
        return null;
    }
}

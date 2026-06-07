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

/**
 * Test fixture for the {@link ServerBridge} swappable backend.
 *
 * <p>Install a stub backend in {@code @BeforeEach} and call
 * {@link #uninstall()} in {@code @AfterEach}. The fixture exposes
 * fluent builders so tests can tweak TPS, max-players, and the
 * online-player <em>count</em> (without constructing real
 * {@code Player} objects — see the class Javadoc on
 * {@link ServerBridge.StubBackend} for why).</p>
 *
 * <p>Why not just use MockBukkit? JitPack now requires
 * authentication for unauthenticated downloads, which breaks CI for
 * any project that pulls a JitPack-only artifact.</p>
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
     * Mutable state behind the stub. The fixture-specific methods
     * ({@link #withTps}, {@link #withMaxPlayers},
     * {@link #withOnlineCount}) drive the
     * {@link ServerBridge.Backend} values that production code reads.
     */
    public static final class State extends ServerBridge.StubBackend {

        public State withTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        public State withMaxPlayers(int max) {
            this.maxPlayers = max;
            return this;
        }

        /**
         * Prime the online-player count that
         * {@code ServerBridge.onlinePlayers().size()} returns. The
         * actual {@code Player} instances are never created — see
         * the class Javadoc for why.
         */
        public State withOnlineCount(int count) {
            this.onlineCount = count;
            return this;
        }
    }
}

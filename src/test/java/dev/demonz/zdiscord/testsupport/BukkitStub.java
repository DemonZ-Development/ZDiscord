package dev.demonz.zdiscord.testsupport;

import dev.demonz.zdiscord.util.ServerBridge;


public final class BukkitStub {

    private BukkitStub() {
    }

    private static State state;


    public static synchronized State install() {
        if (state != null) {
            throw new IllegalStateException("BukkitStub is already installed; call uninstall() first");
        }
        state = new State();
        ServerBridge.setBackend(state);
        return state;
    }


    public static synchronized void uninstall() {
        if (state == null) {
            return;
        }
        ServerBridge.resetBackend();
        state = null;
    }


    public static final class State extends ServerBridge.StubBackend {

        public State withTps(double oneMinute, double fiveMinute, double fifteenMinute) {
            this.tps = new double[] { oneMinute, fiveMinute, fifteenMinute };
            return this;
        }

        public State withMaxPlayers(int max) {
            this.maxPlayers = max;
            return this;
        }


        public State withOnlineCount(int count) {
            this.onlineCount = count;
            return this;
        }
    }
}

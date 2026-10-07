package dev.demonz.zdiscord.util;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Drives the seasonal event clock: announces the countdown, fires the opening
 * and closing boundaries exactly once each per window, and greets late joiners.
 * All Bukkit-facing work is delegated to the callbacks so this stays testable.
 */
public final class HalloweenEasterEgg implements AutoCloseable {

    /**
     * Window lifecycle hooks. Every method has a no-op default so callers only
     * implement what they need.
     */
    public interface PhaseListener {
        default void onWindowOpen(LocalDate windowStart) { }

        default void onFinale(LocalDate windowStart, LocalDate windowEnd) { }

        /**
         * Fired on every check during the month the window opens in, including
         * days far outside any announcement range. Consumers decide how many
         * days out they actually want to say something.
         */
        default void onCountdown(int daysRemaining, LocalDate windowStart) { }
    }

    private static final PhaseListener NO_PHASES = new PhaseListener() { };

    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final Consumer<String> console;
    private final Runnable notifyOperators;
    private final PhaseListener phases;
    private final Set<UUID> greetedPlayers = new HashSet<>();
    private volatile HalloweenWindow window = HalloweenWindow.DEFAULT;
    private ScheduledFuture<?> midnightTask;
    private boolean running;
    private boolean closed;
    private LocalDate openedWindowStart;
    private LocalDate finalisedWindowStart;
    private LocalDate greetedWindowStart;

    public HalloweenEasterEgg(String name, Consumer<String> console, Runnable notifyOperators) {
        this(name, console, notifyOperators, HalloweenWindow.DEFAULT, NO_PHASES);
    }

    public HalloweenEasterEgg(String name, Consumer<String> console,
                              HalloweenWindow window, PhaseListener phases) {
        this(name, console, () -> { }, window, phases);
    }

    public HalloweenEasterEgg(String name, Consumer<String> console, Runnable notifyOperators,
                              HalloweenWindow window, PhaseListener phases) {
        this(Clock.systemDefaultZone(), newDaemonExecutor(name), console, notifyOperators, window, phases);
    }

    HalloweenEasterEgg(Clock clock, ScheduledExecutorService executor,
                       Consumer<String> console, Runnable notifyOperators) {
        this(clock, executor, console, notifyOperators, HalloweenWindow.DEFAULT, NO_PHASES);
    }

    HalloweenEasterEgg(Clock clock, ScheduledExecutorService executor,
                       Consumer<String> console, Runnable notifyOperators,
                       HalloweenWindow window, PhaseListener phases) {
        this.clock = clock;
        this.executor = executor;
        this.console = console;
        this.notifyOperators = notifyOperators;
        this.window = window == null ? HalloweenWindow.DEFAULT : window;
        this.phases = phases == null ? NO_PHASES : phases;
    }

    private static ScheduledExecutorService newDaemonExecutor(String name) {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, name + "-Halloween");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() {
        if (running || closed) return;
        running = true;
        checkDate(true);
        executor.scheduleWithFixedDelay(() -> checkDate(false), 7, 7, TimeUnit.HOURS);
    }

    public synchronized void setWindow(HalloweenWindow window) {
        if (window != null) this.window = window;
    }

    public HalloweenWindow getWindow() {
        return window;
    }

    public synchronized boolean isWindowActive() {
        return running && window.isActive(LocalDate.now(clock));
    }

    public long daysUntilStart() {
        return window.daysUntilStart(LocalDate.now(clock));
    }

    private synchronized void checkDate(boolean startup) {
        if (!running) return;
        LocalDate today = LocalDate.now(clock);
        HalloweenWindow current = window;

        LocalDate finaleFor = current.finaleOn(today);
        if (finaleFor != null && !finaleFor.equals(finalisedWindowStart)) {
            finalisedWindowStart = finaleFor;
            LocalDate lastDay = today.minusDays(1);
            fire(() -> phases.onFinale(finaleFor, lastDay));
        }

        if (current.isActive(today)) {
            LocalDate startFor = current.windowStartOn(today);
            if (startFor != null && !startFor.equals(openedWindowStart)) {
                openedWindowStart = startFor;
                greetedWindowStart = null;
                greetedPlayers.clear();
                LocalDate announced = startFor;
                console.accept("Happy Halloween!");
                fire(() -> phases.onWindowOpen(announced));
            }
            try {
                notifyOperators.run();
            } catch (RuntimeException failure) {
                console.accept("Could not deliver Halloween greeting: " + failure.getMessage());
            }
        } else if (current.isCountdownMonth(today)) {
            long days = current.daysUntilStart(today);
            LocalDate upcoming = current.nextStartOnOrAfter(today);
            if (startup) {
                console.accept("Halloween in " + days + (days == 1 ? " day!" : " days!"));
            }
            fire(() -> phases.onCountdown((int) days, upcoming));
        }

        scheduleNextBoundary();
    }

    private void fire(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            console.accept("Halloween phase callback failed: " + failure.getMessage());
        }
    }

    private void scheduleNextBoundary() {
        if (midnightTask != null) midnightTask.cancel(false);
        ZonedDateTime now = ZonedDateTime.now(clock);
        ZonedDateTime next = nextBoundary(now);
        long delay = Math.max(1L, Duration.between(now, next).toMillis());
        midnightTask = executor.schedule(() -> checkDate(false), delay, TimeUnit.MILLISECONDS);
    }

    private ZonedDateTime nextBoundary(ZonedDateTime now) {
        HalloweenWindow current = window;
        ZonedDateTime best = null;
        for (int year : new int[]{now.getYear() - 1, now.getYear(), now.getYear() + 1}) {
            ZonedDateTime startAt = current.startIn(year).atStartOfDay(now.getZone());
            ZonedDateTime finaleAt = current.endIn(year).plusDays(1).atStartOfDay(now.getZone());
            best = earlierFuture(best, startAt, now);
            best = earlierFuture(best, finaleAt, now);
        }
        return best == null
                ? now.plusDays(1)
                : best;
    }

    private static ZonedDateTime earlierFuture(ZonedDateTime best, ZonedDateTime candidate,
                                              ZonedDateTime now) {
        if (!candidate.isAfter(now)) return best;
        return best == null || candidate.isBefore(best) ? candidate : best;
    }

    public synchronized void greetPlayer(UUID playerId, Consumer<String> sendMessage) {
        if (!running) return;
        LocalDate startFor = window.windowStartOn(LocalDate.now(clock));
        if (startFor == null) return;
        if (!startFor.equals(greetedWindowStart)) {
            greetedWindowStart = startFor;
            greetedPlayers.clear();
        }
        if (greetedPlayers.contains(playerId)) return;
        sendMessage.accept("Happy Halloween!");
        greetedPlayers.add(playerId);
    }

    @Override
    public synchronized void close() {
        running = false;
        closed = true;
        if (midnightTask != null) midnightTask.cancel(false);
        executor.shutdownNow();
        greetedPlayers.clear();
    }
}
package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class HalloweenEasterEggTest {
    @Test
    void startupCountdownAndHolidayAreLimitedToOctober() {
        String[][] cases = {
            {"2026-09-30T12:00:00Z", ""},
            {"2026-10-01T12:00:00Z", "Halloween in 30 days!"},
            {"2026-10-02T12:00:00Z", "Halloween in 29 days!"},
            {"2026-10-30T12:00:00Z", "Halloween in 1 day!"},
            {"2026-10-31T12:00:00Z", "Happy Halloween!"},
            {"2026-11-01T12:00:00Z", ""},
            {"2028-10-31T12:00:00Z", "Happy Halloween!"}
        };
        for (String[] example : cases) {
            try (Fixture f = new Fixture(example[0], "UTC")) {
                f.egg.start();
                assertEquals(example[1].isEmpty() ? List.of() : List.of(example[1]), f.console);
            }
        }
    }

    @Test
    void identicalInstantUsesEachServersLocalCalendar() {
        try (Fixture india = new Fixture("2026-10-30T20:00:00Z", "Asia/Kolkata");
             Fixture california = new Fixture("2026-10-30T20:00:00Z", "America/Los_Angeles")) {
            india.egg.start();
            california.egg.start();
            assertEquals(List.of("Happy Halloween!"), india.console);
            assertEquals(List.of("Halloween in 1 day!"), california.console);
        }
    }

    @Test
    void quietChecksRepeatEverySevenWallClockHours() {
        try (Fixture f = new Fixture("2026-10-01T12:00:00Z", "UTC")) {
            f.egg.start();
            assertEquals(TimeUnit.HOURS.toMillis(7), f.scheduler.periodMillis);
            assertEquals(TimeUnit.HOURS.toMillis(7), f.scheduler.initialDelayMillis);
            f.clock.instant = Instant.parse("2026-10-02T12:00:00Z");
            f.scheduler.periodic.run();
            assertEquals(List.of("Halloween in 30 days!"), f.console);
            assertEquals(0, f.notifications.get());
        }
    }

    @Test
    void localMidnightGreetsWithoutWaitingForTheNextPeriodicCheck() {
        try (Fixture f = new Fixture("2026-10-30T18:29:30Z", "Asia/Kolkata")) {
            f.egg.start();
            assertEquals(30_000, f.scheduler.midnightDelayMillis);
            f.clock.instant = Instant.parse("2026-10-30T18:30:00Z");
            f.scheduler.midnight.run();
            f.scheduler.periodic.run();
            assertEquals(List.of("Halloween in 1 day!", "Happy Halloween!"), f.console);
            assertEquals(2, f.notifications.get());
        }
    }

    @Test
    void daylightSavingChangeIsIncludedInTheMidnightDelay() {
        try (Fixture f = new Fixture("2026-10-24T22:00:00Z", "Europe/Berlin")) {
            f.egg.start();
            assertEquals(TimeUnit.HOURS.toMillis(145), f.scheduler.midnightDelayMillis);
        }
    }

    @Test
    void lateJoiningPlayersAreGreetedOncePerYear() {
        try (Fixture f = new Fixture("2026-10-30T12:00:00Z", "UTC")) {
            UUID player = UUID.randomUUID();
            List<String> messages = new ArrayList<>();
            f.egg.start();
            f.egg.greetPlayer(player, messages::add);
            assertTrue(messages.isEmpty());
            f.clock.instant = Instant.parse("2026-10-31T12:00:00Z");
            f.scheduler.periodic.run();
            f.egg.greetPlayer(player, messages::add);
            f.egg.greetPlayer(player, messages::add);
            assertEquals(List.of("Happy Halloween!"), messages);
            f.clock.instant = Instant.parse("2027-10-31T12:00:00Z");
            f.scheduler.periodic.run();
            f.egg.greetPlayer(player, messages::add);
            assertEquals(List.of("Happy Halloween!", "Happy Halloween!"), messages);
        }
    }

    @Test
    void closingStopsBackgroundChecksAndQueuedPlayerGreetings() {
        try (Fixture f = new Fixture("2026-10-30T12:00:00Z", "UTC")) {
            f.egg.start();
            f.egg.close();
            f.clock.instant = Instant.parse("2026-10-31T12:00:00Z");
            f.scheduler.periodic.run();
            f.egg.greetPlayer(UUID.randomUUID(), ignored -> fail("Greeting after shutdown"));
            assertTrue(f.scheduler.isShutdown());
            assertEquals(List.of("Halloween in 1 day!"), f.console);
            assertEquals(0, f.notifications.get());
        }
    }

    @Test
    void openingBoundaryFiresOnceAndOnlyOnce() {
        try (Fixture f = new Fixture("2026-10-31T12:00:00Z", "UTC")) {
            f.egg.start();
            assertEquals(List.of("2026-10-31"), f.opens);
            assertTrue(f.finales.isEmpty());

            f.scheduler.periodic.run();
            f.scheduler.periodic.run();
            assertEquals(List.of("2026-10-31"), f.opens);
        }
    }

    @Test
    void finaleFiresTheDayAfterAMultiDayWindowCloses() {
        HalloweenWindow window = HalloweenWindow.of(MonthDay.of(10, 28), MonthDay.of(11, 2));
        try (Fixture f = new Fixture("2026-11-02T12:00:00Z", "UTC", window)) {
            f.egg.start();
            assertEquals(List.of("2026-10-28"), f.opens);
            assertTrue(f.finales.isEmpty());

            f.clock.instant = Instant.parse("2026-11-03T12:00:00Z");
            f.scheduler.periodic.run();
            assertEquals(List.of("2026-10-28..2026-11-02"), f.finales);

            f.scheduler.periodic.run();
            assertEquals(1, f.finales.size());
        }
    }

    @Test
    void everyDayOfAMultiDayWindowCountsAsActive() {
        HalloweenWindow window = HalloweenWindow.of(MonthDay.of(10, 30), MonthDay.of(11, 1));
        try (Fixture f = new Fixture("2026-10-30T12:00:00Z", "UTC", window)) {
            f.egg.start();
            f.clock.instant = Instant.parse("2026-10-31T12:00:00Z");
            f.scheduler.periodic.run();
            f.clock.instant = Instant.parse("2026-11-01T12:00:00Z");
            f.scheduler.periodic.run();

            assertEquals(3, f.notifications.get());
            assertEquals(List.of("2026-10-30"), f.opens);
        }
    }

    @Test
    void aFailingPhaseCallbackDoesNotStopTheScheduler() {
        try (Fixture f = new Fixture("2026-10-31T12:00:00Z", "UTC")) {
            HalloweenEasterEgg broken = new HalloweenEasterEgg(f.clock, f.scheduler,
                    f.console::add, f.notifications::incrementAndGet,
                    HalloweenWindow.DEFAULT, new HalloweenEasterEgg.PhaseListener() {
                        @Override
                        public void onWindowOpen(LocalDate windowStart) {
                            throw new IllegalStateException("boom");
                        }
                    });
            broken.start();
            assertTrue(f.console.stream().anyMatch(m -> m.contains("boom")));
            assertEquals(1, f.notifications.get());
            broken.close();
        }
    }

    @Test
    void countdownHookReportsEveryLeadUpDay() {
        try (Fixture f = new Fixture("2026-10-01T12:00:00Z", "UTC")) {
            f.egg.start();
            assertEquals(List.of("30->2026-10-31"), f.countdowns);

            f.clock.instant = Instant.parse("2026-10-02T12:00:00Z");
            f.scheduler.periodic.run();
            assertEquals(List.of("30->2026-10-31", "29->2026-10-31"), f.countdowns);
        }
    }

    @Test
    void countdownStopsOnceTheWindowIsOpen() {
        try (Fixture f = new Fixture("2026-10-30T12:00:00Z", "UTC")) {
            f.egg.start();
            assertEquals(List.of("1->2026-10-31"), f.countdowns);

            f.clock.instant = Instant.parse("2026-10-31T12:00:00Z");
            f.scheduler.periodic.run();
            assertEquals(List.of("1->2026-10-31"), f.countdowns);
            assertEquals(List.of("2026-10-31"), f.opens);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final MutableClock clock;
        final RecordingScheduler scheduler = new RecordingScheduler();
        final List<String> console = new ArrayList<>();
        final AtomicInteger notifications = new AtomicInteger();
        final List<String> opens = new ArrayList<>();
        final List<String> finales = new ArrayList<>();
        final List<String> countdowns = new ArrayList<>();
        final HalloweenEasterEgg egg;

        Fixture(String instant, String zone) {
            this(instant, zone, HalloweenWindow.DEFAULT);
        }

        Fixture(String instant, String zone, HalloweenWindow window) {
            clock = new MutableClock(Instant.parse(instant), ZoneId.of(zone));
            egg = new HalloweenEasterEgg(clock, scheduler, console::add,
                    notifications::incrementAndGet, window, new HalloweenEasterEgg.PhaseListener() {
                        @Override
                        public void onWindowOpen(LocalDate windowStart) {
                            opens.add(windowStart.toString());
                        }

                        @Override
                        public void onFinale(LocalDate windowStart, LocalDate windowEnd) {
                            finales.add(windowStart + ".." + windowEnd);
                        }

                        @Override
                        public void onCountdown(int daysRemaining, LocalDate windowStart) {
                            countdowns.add(daysRemaining + "->" + windowStart);
                        }
                    });
        }

        @Override public void close() { egg.close(); }
    }

    private static final class MutableClock extends Clock {
        Instant instant;
        final ZoneId zone;
        MutableClock(Instant instant, ZoneId zone) { this.instant = instant; this.zone = zone; }
        @Override public Instant instant() { return instant; }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
    }

    private static final class RecordingScheduler extends ScheduledThreadPoolExecutor {
        Runnable periodic;
        Runnable midnight;
        long initialDelayMillis;
        long periodMillis;
        long midnightDelayMillis;

        RecordingScheduler() { super(1); }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initial, long delay, TimeUnit unit) {
            periodic = task;
            initialDelayMillis = unit.toMillis(initial);
            periodMillis = unit.toMillis(delay);
            return super.scheduleWithFixedDelay(task, 365, 365, TimeUnit.DAYS);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            midnight = task;
            midnightDelayMillis = unit.toMillis(delay);
            return super.schedule(task, 365, TimeUnit.DAYS);
        }
    }
}

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
    void publicConstructorUsesConfiguredTimezone() {
        ZoneId ahead = ZoneId.of("Pacific/Kiritimati");
        ZoneId behind = ZoneId.of("Etc/GMT+12");
        MonthDay opening = MonthDay.from(LocalDate.now(ahead));
        HalloweenWindow window = HalloweenWindow.of(opening, opening);
        try (HalloweenEasterEgg aheadEgg = new HalloweenEasterEgg("ahead", ignored -> { },
                () -> { }, window, null, ahead);
             HalloweenEasterEgg behindEgg = new HalloweenEasterEgg("behind", ignored -> { },
                     () -> { }, window, null, behind)) {
            assertEquals(0, aheadEgg.daysUntilStart());
            assertEquals(window.daysUntilStart(LocalDate.now(behind)), behindEgg.daysUntilStart());
            assertTrue(behindEgg.daysUntilStart() > 0);
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
    void joiningBeforeTheOpeningCheckStillGreetsOnlyOnce() {
        try (Fixture f = new Fixture("2026-10-30T12:00:00Z", "UTC")) {
            f.egg.start();
            f.clock.instant = Instant.parse("2026-10-31T00:00:00Z");
            UUID player = UUID.randomUUID();
            List<String> messages = new ArrayList<>();
            f.egg.greetPlayer(player, messages::add);
            f.scheduler.periodic.run();
            f.egg.greetPlayer(player, messages::add);
            assertEquals(List.of("Happy Halloween!"), messages);
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
            assertTrue(f.scheduler.periodicFuture.isCancelled());
            assertTrue(f.scheduler.midnightFuture.isCancelled());
            assertEquals(List.of("Halloween in 1 day!"), f.console);
            assertEquals(0, f.notifications.get());
        }
    }

    @Test
    void openingBoundaryFiresOnceAndOnlyOnce() {
        try (Fixture f = new Fixture("2026-10-31T12:00:00Z", "UTC")) {
            f.egg.start();
            assertEquals(List.of("2026-10-31"), f.opens);
            assertEquals(List.of("2025-10-31..2025-10-31"), f.finales);

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
            assertEquals(List.of("2025-10-28..2025-11-02"), f.finales);
            f.finales.clear();

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
    void runningWindowFinalizesEvenWhenTheClockSkipsSeveralDays() {
        HalloweenWindow window = HalloweenWindow.parse("10-28", "11-02");
        try (Fixture f = new Fixture("2026-11-01T12:00:00Z", "UTC", window)) {
            f.egg.start();
            f.finales.clear();
            f.clock.instant = Instant.parse("2026-11-08T12:00:00Z");
            f.scheduler.periodic.run();
            f.scheduler.periodic.run();
            assertEquals(List.of("2026-10-28..2026-11-02"), f.finales);
        }
    }

    @Test
    void startupRecoversOnlyTheMostRecentlyClosedWindow() {
        try (Fixture f = new Fixture("2026-11-08T12:00:00Z", "UTC")) {
            f.egg.start();
            f.egg.start();
            f.scheduler.periodic.run();
            assertEquals(List.of("2026-10-31..2026-10-31"), f.finales);
            assertTrue(f.opens.isEmpty());
            assertTrue(f.console.isEmpty());
        }
    }

    @Test
    void yearWrappingWindowFinalizesAfterALateJanuaryCheck() {
        HalloweenWindow window = HalloweenWindow.parse("12-29", "01-03");
        try (Fixture f = new Fixture("2027-01-01T12:00:00Z", "UTC", window)) {
            f.egg.start();
            assertEquals(List.of("2025-12-29..2026-01-03"), f.finales);
            f.finales.clear();
            f.clock.instant = Instant.parse("2027-01-08T12:00:00Z");
            f.scheduler.periodic.run();
            assertEquals(List.of("2026-12-29..2027-01-03"), f.finales);
        }
    }

    @Test
    void clockCorrectionsDoNotRepeatOpeningOrFinaleCallbacks() {
        try (Fixture f = new Fixture("2026-10-31T12:00:00Z", "UTC")) {
            f.egg.start();
            f.finales.clear();
            f.clock.instant = Instant.parse("2026-11-02T12:00:00Z");
            f.scheduler.periodic.run();
            f.clock.instant = Instant.parse("2026-10-31T12:00:00Z");
            f.scheduler.periodic.run();
            f.clock.instant = Instant.parse("2026-11-01T12:00:00Z");
            f.scheduler.periodic.run();
            assertEquals(List.of("2026-10-31"), f.opens);
            assertEquals(List.of("2026-10-31..2026-10-31"), f.finales);
        }
    }

    @Test
    void changingTheWindowRechecksPhasesAndReschedulesItsBoundary() {
        try (Fixture f = new Fixture("2026-10-30T12:00:00Z", "UTC")) {
            f.egg.start();
            ScheduledFuture<?> originalBoundary = f.scheduler.midnightFuture;
            f.egg.setWindow(HalloweenWindow.parse("10-30", "11-02"));
            assertTrue(originalBoundary.isCancelled());
            assertEquals(List.of("2026-10-30"), f.opens);
            assertEquals(TimeUnit.HOURS.toMillis(84), f.scheduler.midnightDelayMillis);
            f.scheduler.periodic.run();
            assertEquals(1, f.opens.size());
        }
    }

    @Test
    void countdownHookCanCrossTheOpeningMonthBoundary() {
        try (Fixture f = new Fixture("2026-10-25T12:00:00Z", "UTC",
                HalloweenWindow.parse("11-01", "11-03"))) {
            f.egg.start();
            assertEquals(List.of("7->2026-11-01"), f.countdowns);
            assertTrue(f.console.isEmpty());
        }
    }

    @Test
    void aCallbackCanCloseTheEngineWithoutSchedulingMoreTasks() {
        try (Fixture f = new Fixture("2026-10-31T12:00:00Z", "UTC")) {
            HalloweenEasterEgg[] holder = new HalloweenEasterEgg[1];
            holder[0] = new HalloweenEasterEgg(f.clock, f.scheduler, f.console::add,
                    f.notifications::incrementAndGet, HalloweenWindow.DEFAULT,
                    new HalloweenEasterEgg.PhaseListener() {
                        @Override
                        public void onWindowOpen(LocalDate windowStart) {
                            holder[0].close();
                        }
                    });
            holder[0].start();
            assertTrue(f.scheduler.isShutdown());
            assertNull(f.scheduler.periodic);
            assertNull(f.scheduler.midnight);
            assertEquals(0, f.notifications.get());
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
        ScheduledFuture<?> periodicFuture;
        ScheduledFuture<?> midnightFuture;

        RecordingScheduler() { super(1); }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initial, long delay, TimeUnit unit) {
            periodic = task;
            initialDelayMillis = unit.toMillis(initial);
            periodMillis = unit.toMillis(delay);
            periodicFuture = super.scheduleWithFixedDelay(task, 365, 365, TimeUnit.DAYS);
            return periodicFuture;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
            midnight = task;
            midnightDelayMillis = unit.toMillis(delay);
            midnightFuture = super.schedule(task, 365, TimeUnit.DAYS);
            return midnightFuture;
        }
    }
}

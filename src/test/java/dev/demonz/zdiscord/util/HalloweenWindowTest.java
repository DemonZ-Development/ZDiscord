package dev.demonz.zdiscord.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.MonthDay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HalloweenWindowTest {

    private static final HalloweenWindow SINGLE_DAY =
            HalloweenWindow.of(MonthDay.of(10, 31), MonthDay.of(10, 31));
    private static final HalloweenWindow FIVE_DAYS =
            HalloweenWindow.of(MonthDay.of(10, 28), MonthDay.of(11, 2));

    @Test
    void defaultsToASingleHalloweenDay() {
        assertEquals(MonthDay.of(10, 31), HalloweenWindow.DEFAULT.start());
        assertEquals(MonthDay.of(10, 31), HalloweenWindow.DEFAULT.end());
    }

    @Test
    void singleDayWindowIsOnlyActiveOnHalloween() {
        assertFalse(SINGLE_DAY.isActive(LocalDate.of(2026, 10, 30)));
        assertTrue(SINGLE_DAY.isActive(LocalDate.of(2026, 10, 31)));
        assertFalse(SINGLE_DAY.isActive(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void multiDayWindowIncludesBothBoundaries() {
        assertFalse(FIVE_DAYS.isActive(LocalDate.of(2026, 10, 27)));
        assertTrue(FIVE_DAYS.isActive(LocalDate.of(2026, 10, 28)));
        assertTrue(FIVE_DAYS.isActive(LocalDate.of(2026, 10, 31)));
        assertTrue(FIVE_DAYS.isActive(LocalDate.of(2026, 11, 1)));
        assertTrue(FIVE_DAYS.isActive(LocalDate.of(2026, 11, 2)));
        assertFalse(FIVE_DAYS.isActive(LocalDate.of(2026, 11, 3)));
    }

    @Test
    void windowThatWrapsIntoNextYearResolvesItsEndDate() {
        assertEquals(LocalDate.of(2026, 11, 2), FIVE_DAYS.endIn(2026));
        assertEquals(LocalDate.of(2026, 10, 28), FIVE_DAYS.windowStartOn(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void decemberWindowStaysActiveIntoJanuaryOfTheFollowingYear() {
        HalloweenWindow window = HalloweenWindow.parse("12-29", "01-03");
        assertEquals(LocalDate.of(2027, 1, 3), window.endIn(2026));
        assertTrue(window.isActive(LocalDate.of(2026, 12, 29)));
        assertTrue(window.isActive(LocalDate.of(2027, 1, 3)));
        assertFalse(window.isActive(LocalDate.of(2027, 1, 4)));
        assertEquals(LocalDate.of(2026, 12, 29), window.windowStartOn(LocalDate.of(2027, 1, 1)));
        assertEquals(LocalDate.of(2026, 12, 29), window.finaleOn(LocalDate.of(2027, 1, 4)));
    }

    @Test
    void leapDayWindowUsesFebruary28InNonLeapYears() {
        HalloweenWindow window = HalloweenWindow.parse("02-29", "03-01");
        assertEquals(LocalDate.of(2027, 2, 28), window.startIn(2027));
        assertEquals(LocalDate.of(2028, 2, 29), window.startIn(2028));
        assertTrue(window.isActive(LocalDate.of(2027, 2, 28)));
        assertFalse(window.isActive(LocalDate.of(2028, 2, 28)));
        assertTrue(window.isActive(LocalDate.of(2028, 2, 29)));
        assertEquals(LocalDate.of(2028, 2, 29), window.finaleOn(LocalDate.of(2028, 3, 2)));
    }

    @Test
    void latestClosedWindowSupportsLateAndYearWrappingRecovery() {
        assertEquals(LocalDate.of(2026, 10, 31),
                SINGLE_DAY.mostRecentClosedStart(LocalDate.of(2026, 11, 8)));
        assertEquals(LocalDate.of(2025, 10, 31),
                SINGLE_DAY.mostRecentClosedStart(LocalDate.of(2026, 10, 31)));

        HalloweenWindow window = HalloweenWindow.parse("12-29", "01-03");
        assertEquals(LocalDate.of(2025, 12, 29),
                window.mostRecentClosedStart(LocalDate.of(2027, 1, 1)));
        assertEquals(LocalDate.of(2026, 12, 29),
                window.mostRecentClosedStart(LocalDate.of(2027, 1, 4)));
    }

    @Test
    void finaleLandsTheDayAfterTheWindowCloses() {
        assertEquals(LocalDate.of(2026, 10, 31), SINGLE_DAY.finaleOn(LocalDate.of(2026, 11, 1)));
        assertNull(SINGLE_DAY.finaleOn(LocalDate.of(2026, 10, 31)));
        assertNull(SINGLE_DAY.finaleOn(LocalDate.of(2026, 11, 2)));
        assertEquals(LocalDate.of(2026, 10, 28), FIVE_DAYS.finaleOn(LocalDate.of(2026, 11, 3)));
    }

    @Test
    void countdownIsAnnouncedDuringTheOpeningMonthOnly() {
        assertFalse(SINGLE_DAY.isCountdownMonth(LocalDate.of(2026, 9, 30)));
        assertTrue(SINGLE_DAY.isCountdownMonth(LocalDate.of(2026, 10, 1)));
        assertTrue(SINGLE_DAY.isCountdownMonth(LocalDate.of(2026, 10, 30)));
        assertFalse(SINGLE_DAY.isCountdownMonth(LocalDate.of(2026, 10, 31)));
        assertFalse(FIVE_DAYS.isCountdownMonth(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void daysUntilStartCountsDownToTheNextWindow() {
        assertEquals(30L, SINGLE_DAY.daysUntilStart(LocalDate.of(2026, 10, 1)));
        assertEquals(1L, SINGLE_DAY.daysUntilStart(LocalDate.of(2026, 10, 30)));
        assertEquals(364L, SINGLE_DAY.daysUntilStart(LocalDate.of(2026, 11, 1)));
    }

    @Test
    void malformedConfigFallsBackToTheDefaultWindow() {
        HalloweenWindow parsed = HalloweenWindow.parse("not-a-date", "");
        assertEquals(MonthDay.of(10, 31), parsed.start());
        assertEquals(MonthDay.of(10, 31), parsed.end());

        assertEquals(MonthDay.of(10, 31), HalloweenWindow.parse(null, null).start());
    }

    @Test
    void configuredWindowIsHonoured() {
        HalloweenWindow parsed = HalloweenWindow.parse("12-24", "12-26");
        assertTrue(parsed.isActive(LocalDate.of(2026, 12, 25)));
        assertFalse(parsed.isActive(LocalDate.of(2026, 12, 27)));
    }
}

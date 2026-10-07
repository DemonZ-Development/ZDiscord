package dev.demonz.zdiscord.util;

import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;

/**
 * A recurring seasonal window described by two month/day boundaries.
 * When {@code end} sorts before {@code start} the window wraps into the
 * following year, so {@code 10-28} to {@code 11-02} spans five days.
 */
public record HalloweenWindow(MonthDay start, MonthDay end) {

    public static final HalloweenWindow DEFAULT =
            new HalloweenWindow(MonthDay.of(10, 31), MonthDay.of(10, 31));

    public HalloweenWindow {
        if (start == null) start = DEFAULT.start;
        if (end == null) end = DEFAULT.end;
    }

    public static HalloweenWindow of(MonthDay start, MonthDay end) {
        return new HalloweenWindow(start, end);
    }

    /**
     * Parses {@code MM-DD} pairs, falling back to the default window when a
     * value is blank or malformed so a typo cannot disable the event.
     */
    public static HalloweenWindow parse(String startText, String endText) {
        return of(parseMonthDay(startText, DEFAULT.start), parseMonthDay(endText, DEFAULT.end));
    }

    private static MonthDay parseMonthDay(String text, MonthDay fallback) {
        if (text == null) return fallback;
        String trimmed = text.trim();
        int separator = trimmed.indexOf('-');
        if (separator < 0) return fallback;
        try {
            return MonthDay.of(
                    Integer.parseInt(trimmed.substring(0, separator).trim()),
                    Integer.parseInt(trimmed.substring(separator + 1).trim()));
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public LocalDate startIn(int year) {
        return start.atYear(year);
    }

    public LocalDate endIn(int year) {
        return end.isBefore(start) ? end.atYear(year + 1) : end.atYear(year);
    }

    public boolean isActive(LocalDate today) {
        return windowContaining(today) != null;
    }

    /**
     * The start date of the window {@code today} falls inside, or null.
     */
    public LocalDate windowStartOn(LocalDate today) {
        return windowContaining(today);
    }

    /**
     * The start date of the window that closed the day before {@code today},
     * or null. The finale deliberately lands after the final day so the last
     * day's kills are already counted when the winner is read.
     */
    public LocalDate finaleOn(LocalDate today) {
        for (int year : new int[]{today.getYear() - 1, today.getYear()}) {
            if (endIn(year).plusDays(1).equals(today)) {
                return startIn(year);
            }
        }
        return null;
    }

    public LocalDate nextStartOnOrAfter(LocalDate today) {
        LocalDate candidate = startIn(today.getYear());
        return candidate.isBefore(today) ? startIn(today.getYear() + 1) : candidate;
    }

    public long daysUntilStart(LocalDate today) {
        return ChronoUnit.DAYS.between(today, nextStartOnOrAfter(today));
    }

    /**
     * Countdown announcements only fire during the calendar month the window
     * opens in, so a single-day window stays quiet for the rest of the year.
     */
    public boolean isCountdownMonth(LocalDate today) {
        LocalDate from = startIn(today.getYear());
        return today.getMonthValue() == from.getMonthValue() && today.isBefore(from);
    }

    private LocalDate windowContaining(LocalDate today) {
        for (int year : new int[]{today.getYear() - 1, today.getYear()}) {
            LocalDate from = startIn(year);
            if (!today.isBefore(from) && !today.isAfter(endIn(year))) {
                return from;
            }
        }
        return null;
    }
}
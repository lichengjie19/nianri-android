package com.nianri.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/** Identity used when syncing a date, independent of the calendar reminder time. */
final class CalendarEventKey {
    final long calendarId;
    final String title;
    final LocalDate date;

    CalendarEventKey(long calendarId, String title, LocalDate date) {
        this.calendarId = calendarId;
        this.title = title;
        this.date = date;
    }

    static CalendarEventKey fromInstance(
            long calendarId, String title, long begin, boolean allDay, ZoneId localZone
    ) {
        // CalendarContract represents all-day dates at midnight UTC, even in zones
        // where that instant falls on the previous local date.
        ZoneId zone = allDay ? ZoneOffset.UTC : localZone;
        LocalDate date = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate();
        return new CalendarEventKey(calendarId, title, date);
    }

    long queryBegin(ZoneId localZone) {
        return Math.min(startOfDay(date, localZone), startOfDay(date, ZoneOffset.UTC));
    }

    long queryEnd(ZoneId localZone) {
        LocalDate nextDay = date.plusDays(1);
        return Math.max(startOfDay(nextDay, localZone), startOfDay(nextDay, ZoneOffset.UTC)) - 1L;
    }

    private static long startOfDay(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CalendarEventKey)) {
            return false;
        }
        CalendarEventKey key = (CalendarEventKey) other;
        return calendarId == key.calendarId && Objects.equals(title, key.title)
                && date.equals(key.date);
    }

    @Override
    public int hashCode() {
        return Objects.hash(calendarId, title, date);
    }
}

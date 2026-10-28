package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

public final class CalendarEventKeyTest {
    private static final LocalDate DATE = LocalDate.of(2026, 10, 27);
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Test
    public void sameTitleOnSameDateSkipsDifferentReminderTimes() {
        CalendarEventKey desired = new CalendarEventKey(1L, "妈妈生日", DATE);
        CalendarEventKey morning = instance(1L, "妈妈生日", DATE, 8, SHANGHAI);
        CalendarEventKey evening = instance(1L, "妈妈生日", DATE, 20, SHANGHAI);

        assertEquals(desired, morning);
        assertEquals(desired, evening);
    }

    @Test
    public void differentCalendarTitleOrDateIsNotDuplicate() {
        CalendarEventKey desired = new CalendarEventKey(1L, "妈妈生日", DATE);

        assertNotEquals(desired, new CalendarEventKey(2L, "妈妈生日", DATE));
        assertNotEquals(desired, new CalendarEventKey(1L, "爸爸生日", DATE));
        assertNotEquals(desired, new CalendarEventKey(1L, "妈妈生日", DATE.plusDays(1)));
    }

    @Test
    public void successfulBatchItemsShareIdentityWithoutProviderRefresh() {
        Set<CalendarEventKey> written = new HashSet<>();
        written.add(new CalendarEventKey(1L, "生日", DATE));

        assertTrue(written.contains(instance(1L, "生日", DATE, 9, SHANGHAI)));
        assertFalse(written.contains(instance(2L, "生日", DATE, 9, SHANGHAI)));
        assertFalse(written.contains(instance(1L, "生日", DATE.plusYears(1), 9, SHANGHAI)));
    }

    @Test
    public void allDayDateUsesUtcInNegativeTimezone() {
        ZoneId losAngeles = ZoneId.of("America/Los_Angeles");
        long begin = DATE.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();

        assertEquals(new CalendarEventKey(1L, "生日", DATE),
                CalendarEventKey.fromInstance(1L, "生日", begin, true, losAngeles));
        assertEquals(new CalendarEventKey(1L, "生日", DATE.minusDays(1)),
                CalendarEventKey.fromInstance(1L, "生日", begin, false, losAngeles));
    }

    @Test
    public void recurringInstanceUsesOccurrenceDateRatherThanSeriesStart() {
        CalendarEventKey nextYear = new CalendarEventKey(1L, "生日", DATE.plusYears(1));

        assertEquals(nextYear, instance(1L, "生日", DATE.plusYears(1), 7, SHANGHAI));
        assertNotEquals(nextYear, instance(1L, "生日", DATE, 7, SHANGHAI));
    }

    @Test
    public void searchRangeCoversLocalAndUtcDatesInBothDirections() {
        assertDateRange(DATE, ZoneId.of("Pacific/Kiritimati"));
        assertDateRange(DATE, ZoneId.of("America/Los_Angeles"));
        assertDateRange(DATE, ZoneOffset.UTC);
    }

    @Test
    public void searchRangeUsesNextMidnightAcrossDaylightSavingChanges() {
        assertDateRange(LocalDate.of(2026, 3, 8), ZoneId.of("America/Los_Angeles"));
        assertDateRange(LocalDate.of(2026, 11, 1), ZoneId.of("America/Los_Angeles"));
    }

    private static void assertDateRange(LocalDate date, ZoneId zone) {
        CalendarEventKey key = new CalendarEventKey(1L, "日期", date);
        long localBegin = date.atStartOfDay(zone).toInstant().toEpochMilli();
        long localEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L;
        long utcBegin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        long utcEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() - 1L;

        assertEquals(Math.min(localBegin, utcBegin), key.queryBegin(zone));
        assertEquals(Math.max(localEnd, utcEnd), key.queryEnd(zone));
        assertTrue(key.queryBegin(zone) <= localBegin);
        assertTrue(key.queryBegin(zone) <= utcBegin);
        assertTrue(key.queryEnd(zone) >= localEnd);
        assertTrue(key.queryEnd(zone) >= utcEnd);
    }

    private static CalendarEventKey instance(
            long calendarId, String title, LocalDate date, int hour, ZoneId zone
    ) {
        long begin = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli();
        return CalendarEventKey.fromInstance(calendarId, title, begin, false, zone);
    }
}

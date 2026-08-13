package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;

public final class DateCalculatorTest {
    @Test
    public void switchingTomorrowFromSolarToLunarKeepsTheSameDay() {
        DateEvent event = event(DateEvent.CALENDAR_SOLAR, 2024, 2, 10, true);
        event.yearKnown = false;

        LocalDate represented = DateCalculator.convertCalendar(
                event,
                DateEvent.CALENDAR_LUNAR,
                LocalDate.of(2024, 2, 9)
        );

        assertEquals(LocalDate.of(2024, 2, 10), represented);
        assertEquals(DateEvent.CALENDAR_LUNAR, event.calendarType);
        assertEquals(2024, event.year);
        assertEquals(1, event.month);
        assertEquals(1, event.day);
        assertFalse(event.leapMonth);
    }

    @Test
    public void switchingBackToSolarRestoresTheSameDate() {
        DateEvent event = event(DateEvent.CALENDAR_LUNAR, 2024, 1, 1, true);
        event.yearKnown = false;

        DateCalculator.convertCalendar(
                event,
                DateEvent.CALENDAR_SOLAR,
                LocalDate.of(2024, 2, 9)
        );

        assertEquals(DateEvent.CALENDAR_SOLAR, event.calendarType);
        assertEquals(2024, event.year);
        assertEquals(2, event.month);
        assertEquals(10, event.day);
        assertFalse(event.leapMonth);
    }
    @Test
    public void knownChineseNewYearConvertsOffline() {
        assertEquals(
                LocalDate.of(2026, 2, 17),
                DateCalculator.lunarToSolar(2026, 1, 1, false, true)
        );
    }

    @Test
    public void recurringSolarDateRollsIntoNextYear() {
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.year = 2026;
        event.month = 9;
        event.day = 16;
        event.yearly = true;

        Occurrence occurrence = DateCalculator.occurrence(event, LocalDate.of(2026, 9, 17));

        assertEquals(LocalDate.of(2027, 9, 16), occurrence.solarDate);
        assertFalse(occurrence.expired);
    }

    @Test
    public void pastOneTimeDateBecomesExpired() {
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.year = 2026;
        event.month = 8;
        event.day = 10;
        event.yearly = false;

        Occurrence occurrence = DateCalculator.occurrence(event, LocalDate.of(2026, 8, 12));

        assertTrue(occurrence.expired);
        assertEquals(-2, occurrence.daysFromToday);
    }

    @Test
    public void recurringLunarDatePreservesLunarMonthAndDay() {
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_LUNAR;
        event.year = 2026;
        event.month = 8;
        event.day = 6;
        event.yearly = true;

        Occurrence first = DateCalculator.occurrence(event, LocalDate.of(2026, 1, 1));
        Occurrence next = DateCalculator.occurrence(event, first.solarDate.plusDays(1));

        assertTrue(next.solarDate.isAfter(first.solarDate));
        assertTrue(next.primaryDate.contains("八月初六"));
    }

    @Test
    public void leapMonthMetadataIsAvailableOffline() {
        assertEquals(6, DateCalculator.leapMonthOf(2025));
        assertTrue(DateCalculator.isValidLunarDate(2025, 6, 1, true));
        assertFalse(DateCalculator.isValidLunarDate(2026, 6, 1, true));
    }

    @Test
    public void recurringDateDoesNotNeedARecordedYear() {
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.yearKnown = false;
        event.year = 1900;
        event.month = 9;
        event.day = 16;
        event.yearly = true;

        Occurrence occurrence = DateCalculator.occurrence(event, LocalDate.of(2026, 9, 17));

        assertEquals(LocalDate.of(2027, 9, 16), occurrence.solarDate);
        assertFalse(occurrence.expired);
    }

    @Test
    public void oneTimeDateWithoutYearAnchorsToNextOccurrence() {
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_SOLAR;
        event.yearKnown = false;
        event.year = 2026;
        event.month = 1;
        event.day = 10;
        event.yearly = false;

        DateCalculator.anchorOneTimeDateWithoutYear(event, LocalDate.of(2026, 8, 12));

        assertEquals(2027, event.year);
        assertEquals(
                LocalDate.of(2027, 1, 10),
                DateCalculator.occurrence(event, LocalDate.of(2026, 8, 12)).solarDate
        );
    }

    @Test
    public void lunarDateWithoutYearUsesNextLunarOccurrence() {
        DateEvent event = new DateEvent();
        event.calendarType = DateEvent.CALENDAR_LUNAR;
        event.yearKnown = false;
        event.year = 1900;
        event.month = 1;
        event.day = 1;
        event.yearly = true;

        Occurrence occurrence = DateCalculator.occurrence(event, LocalDate.of(2026, 2, 18));

        assertTrue(occurrence.solarDate.isAfter(LocalDate.of(2026, 2, 18)));
        assertTrue(occurrence.primaryDate.contains("正月初一"));
    }

    private static DateEvent event(
            String calendarType,
            int year,
            int month,
            int day,
            boolean yearly
    ) {
        DateEvent event = new DateEvent();
        event.calendarType = calendarType;
        event.year = year;
        event.month = month;
        event.day = day;
        event.yearly = yearly;
        return event;
    }
}

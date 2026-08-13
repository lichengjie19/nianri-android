package com.nianri.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ReminderTimeTest {
    @Test
    public void supportsEveryWholeHour() {
        assertEquals("00:00", ReminderTime.format(0));
        assertEquals("09:00", ReminderTime.format(9));
        assertEquals("23:00", ReminderTime.format(23));
    }

    @Test
    public void invalidSavedHourFallsBackToNine() {
        assertEquals(ReminderTime.DEFAULT_HOUR, ReminderTime.normalizeHour(-1));
        assertEquals(ReminderTime.DEFAULT_HOUR, ReminderTime.normalizeHour(24));
    }

    @Test
    public void combinesReminderDateAndSelectedHour() {
        assertEquals(
                LocalDateTime.of(2026, 8, 13, 18, 0),
                ReminderTime.at(LocalDate.of(2026, 8, 13), 18)
        );
    }
}

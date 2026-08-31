package com.nianri.app;

import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DateWidgetRefreshScheduleTest {
    @Test
    public void schedulesFiveSecondsAfterNextLocalMidnight() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.of(2026, 8, 31, 10, 8, 0, 0, zone);

        long triggerAt = DateWidgetRefreshSchedule.nextTriggerAtMillis(
                now.toInstant().toEpochMilli(),
                zone
        );
        ZonedDateTime trigger = Instant.ofEpochMilli(triggerAt).atZone(zone);

        assertTrue(triggerAt > now.toInstant().toEpochMilli());
        assertEquals(LocalDate.of(2026, 9, 1), trigger.toLocalDate());
        assertEquals(LocalTime.of(0, 0, 5), trigger.toLocalTime());
    }

    @Test
    public void midnightSchedulesTheFollowingDayInsteadOfRepeatingImmediately() {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 1, 0, 0, 5, 0, zone);

        long triggerAt = DateWidgetRefreshSchedule.nextTriggerAtMillis(
                now.toInstant().toEpochMilli(),
                zone
        );
        ZonedDateTime trigger = Instant.ofEpochMilli(triggerAt).atZone(zone);

        assertEquals(LocalDate.of(2026, 9, 2), trigger.toLocalDate());
        assertEquals(LocalTime.of(0, 0, 5), trigger.toLocalTime());
    }
}

package com.nianri.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class EventCountdownTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 13);

    @Test
    public void todayBeforeReminderShowsHoursAndMinutes() {
        DateEvent event = eventAt(18);

        String text = EventCountdown.text(
                event,
                occurrence(TODAY, 0, false),
                LocalDateTime.of(2026, 8, 13, 15, 23)
        );

        assertEquals("2小时37分", text);
        assertTrue(EventCountdown.isHourMinute(text));
    }

    @Test
    public void todayLessThanOneHourShowsMinutes() {
        DateEvent event = eventAt(18);

        assertEquals(
                "15分钟",
                EventCountdown.text(
                        event,
                        occurrence(TODAY, 0, false),
                        LocalDateTime.of(2026, 8, 13, 17, 45)
                )
        );
    }

    @Test
    public void todayAfterReminderStaysToday() {
        DateEvent event = eventAt(18);

        assertEquals(
                "今天",
                EventCountdown.text(
                        event,
                        occurrence(TODAY, 0, false),
                        LocalDateTime.of(2026, 8, 13, 18, 0)
                )
        );
        assertFalse(EventCountdown.isHourMinute("今天"));
    }

    @Test
    public void todayWithoutSameDayNotificationDoesNotShowTimeCountdown() {
        DateEvent event = eventAt(18);
        event.reminderDays.clear();
        event.reminderDays.add(1);

        assertEquals(
                "今天",
                EventCountdown.text(
                        event,
                        occurrence(TODAY, 0, false),
                        LocalDateTime.of(2026, 8, 13, 15, 23)
                )
        );
    }

    @Test
    public void todayWithReminderDisabledDoesNotShowTimeCountdown() {
        DateEvent event = eventAt(18);
        event.reminderEnabled = false;

        assertEquals(
                "今天",
                EventCountdown.text(
                        event,
                        occurrence(TODAY, 0, false),
                        LocalDateTime.of(2026, 8, 13, 15, 23)
                )
        );
    }

    @Test
    public void otherDatesKeepDayBasedLabels() {
        DateEvent event = eventAt(18);
        assertEquals(
                "明天",
                EventCountdown.text(
                        event,
                        occurrence(TODAY.plusDays(1), 1, false),
                        LocalDateTime.of(2026, 8, 13, 15, 23)
                )
        );
        assertEquals(
                "已过 2 天",
                EventCountdown.text(
                        event,
                        occurrence(TODAY.minusDays(2), -2, true),
                        LocalDateTime.of(2026, 8, 13, 15, 23)
                )
        );
    }

    private static DateEvent eventAt(int hour) {
        DateEvent event = new DateEvent();
        event.reminderHour = hour;
        return event;
    }

    private static Occurrence occurrence(LocalDate date, long days, boolean expired) {
        return new Occurrence(date, days, expired, "", "");
    }
}

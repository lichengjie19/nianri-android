package com.nianri.app;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;

public final class ReminderTime {
    public static final int DEFAULT_HOUR = 9;

    private ReminderTime() {
    }

    public static int normalizeHour(int hour) {
        return hour >= 0 && hour <= 23 ? hour : DEFAULT_HOUR;
    }

    public static LocalDateTime at(LocalDate date, int hour) {
        return date.atTime(normalizeHour(hour), 0);
    }

    public static String format(int hour) {
        return String.format(Locale.CHINA, "%02d:00", normalizeHour(hour));
    }
}

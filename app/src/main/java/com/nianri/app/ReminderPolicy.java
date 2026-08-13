package com.nianri.app;

import java.time.LocalDate;

public final class ReminderPolicy {
    public static final long ON_TIME_GRACE_MILLIS = 2 * 60 * 1000L;

    private ReminderPolicy() {
    }

    public static boolean isMissedToday(
            LocalDate reminderDate,
            LocalDate today,
            long triggerAtMillis,
            long nowMillis,
            boolean delivered
    ) {
        return !delivered
                && nowMillis >= triggerAtMillis + ON_TIME_GRACE_MILLIS
                && reminderDate.equals(today);
    }

    public static boolean isDueNow(
            LocalDate reminderDate,
            LocalDate today,
            long triggerAtMillis,
            long nowMillis,
            boolean delivered
    ) {
        return !delivered
                && reminderDate.equals(today)
                && nowMillis >= triggerAtMillis
                && nowMillis < triggerAtMillis + ON_TIME_GRACE_MILLIS;
    }

    public static String deliveryKey(long eventId, long occurrenceEpochDay, int offset, int hour) {
        return eventId + ":" + occurrenceEpochDay + ":" + offset + ":" + ReminderTime.normalizeHour(hour);
    }
}

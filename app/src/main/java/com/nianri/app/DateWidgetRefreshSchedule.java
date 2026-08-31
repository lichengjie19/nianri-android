package com.nianri.app;

import java.time.Instant;
import java.time.ZoneId;

final class DateWidgetRefreshSchedule {
    private static final long MIDNIGHT_SAFETY_DELAY_SECONDS = 5L;

    private DateWidgetRefreshSchedule() {
    }

    static long nextTriggerAtMillis(long nowMillis, ZoneId zoneId) {
        return Instant.ofEpochMilli(nowMillis)
                .atZone(zoneId)
                .toLocalDate()
                .plusDays(1L)
                .atStartOfDay(zoneId)
                .plusSeconds(MIDNIGHT_SAFETY_DELAY_SECONDS)
                .toInstant()
                .toEpochMilli();
    }
}

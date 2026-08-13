package com.nianri.app;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

public final class EventCountdown {
    private EventCountdown() {
    }

    public static String text(DateEvent event, Occurrence occurrence, LocalDateTime now) {
        long days = occurrence.daysFromToday;
        if (occurrence.expired) {
            return "已过 " + Math.abs(days) + " 天";
        }
        if (days == 0 && event.reminderEnabled && event.reminderDays.contains(0)) {
            LocalDateTime reminderAt = ReminderTime.at(occurrence.solarDate, event.reminderHour);
            if (now.isBefore(reminderAt)) {
                long seconds = ChronoUnit.SECONDS.between(now, reminderAt);
                long minutes = Math.max(1L, (seconds + 59L) / 60L);
                long hours = minutes / 60L;
                long remainingMinutes = minutes % 60L;
                if (hours > 0) {
                    return hours + "小时" + remainingMinutes + "分";
                }
                return remainingMinutes + "分钟";
            }
        }
        if (days == 0) return "今天";
        if (days == 1) return "明天";
        return days + " 天后";
    }

    public static boolean isHourMinute(String text) {
        return text.contains("小时") || text.endsWith("分钟");
    }
}

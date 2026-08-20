package com.nianri.app;

import java.time.LocalDate;
import java.util.List;

final class DateWidgetModel {
    static final long AUTO_EVENT_ID = 0L;

    private DateWidgetModel() {
    }

    static DateEvent resolveEvent(List<DateEvent> events, long selectedEventId, LocalDate today) {
        if (selectedEventId != AUTO_EVENT_ID) {
            for (DateEvent event : events) {
                if (event.id == selectedEventId) {
                    return event;
                }
            }
        }

        DateEvent nearest = null;
        long nearestDays = Long.MAX_VALUE;
        for (DateEvent event : events) {
            try {
                Occurrence occurrence = DateCalculator.occurrence(event, today);
                if (occurrence.expired) {
                    continue;
                }
                if (nearest == null
                        || occurrence.daysFromToday < nearestDays
                        || (occurrence.daysFromToday == nearestDays
                        && event.createdAt < nearest.createdAt)) {
                    nearest = event;
                    nearestDays = occurrence.daysFromToday;
                }
            } catch (RuntimeException ignored) {
                // A malformed saved date should not prevent the remaining widgets from updating.
            }
        }
        return nearest;
    }

    static CardContent content(DateEvent event, LocalDate today) {
        if (event == null) {
            return new CardContent(
                    "添加重要日期",
                    "＋",
                    "",
                    "点击打开念日",
                    "创建后会在这里显示倒数详情",
                    "念日桌面卡片，点击添加重要日期"
            );
        }

        Occurrence occurrence = DateCalculator.occurrence(event, today);
        long days = occurrence.daysFromToday;
        String title = event.title == null || event.title.trim().isEmpty()
                ? "重要日期"
                : event.title.trim();
        String number;
        String unit;
        if (days < 0) {
            number = String.valueOf(Math.abs(days));
            unit = "天前";
        } else if (days == 0) {
            number = "今天";
            unit = "";
        } else if (days == 1) {
            number = "明天";
            unit = "";
        } else {
            number = String.valueOf(days);
            unit = "天后";
        }
        String date = DateCalculator.fullSolarText(occurrence.solarDate);
        String detail = occurrence.secondaryDate
                + (event.yearly ? " · 每年重复" : "");
        String timing;
        if (days < 0) {
            timing = "已过" + Math.abs(days) + "天";
        } else if (days == 0) {
            timing = "就是今天";
        } else if (days == 1) {
            timing = "就是明天";
        } else {
            timing = "还有" + days + "天";
        }
        return new CardContent(
                title,
                number,
                unit,
                date,
                detail,
                title + "，" + timing + "，" + date + "，" + detail
        );
    }

    static final class CardContent {
        final String title;
        final String number;
        final String unit;
        final String date;
        final String detail;
        final String contentDescription;

        CardContent(
                String title,
                String number,
                String unit,
                String date,
                String detail,
                String contentDescription
        ) {
            this.title = title;
            this.number = number;
            this.unit = unit;
            this.date = date;
            this.detail = detail;
            this.contentDescription = contentDescription;
        }
    }
}

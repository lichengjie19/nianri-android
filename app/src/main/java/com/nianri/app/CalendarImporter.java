package com.nianri.app;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public final class CalendarImporter {
    private CalendarImporter() {
    }

    public static List<DateEvent> readUpcoming(Context context, int daysAhead) {
        ContentResolver resolver = context.getContentResolver();
        long begin = System.currentTimeMillis();
        long end = LocalDate.now().plusDays(daysAhead + 1L)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli();
        String[] projection = {
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY,
                CalendarContract.Instances.RRULE
        };

        Map<String, DateEvent> unique = new LinkedHashMap<>();
        android.net.Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        android.content.ContentUris.appendId(builder, begin);
        android.content.ContentUris.appendId(builder, end);
        try (Cursor cursor = resolver.query(builder.build(), projection, null, null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor == null) {
                return new ArrayList<>();
            }
            while (cursor.moveToNext() && unique.size() < 100) {
                long eventId = cursor.getLong(0);
                String title = cursor.getString(1);
                long startMillis = cursor.getLong(2);
                String rrule = cursor.isNull(4) ? "" : cursor.getString(4);
                if (title == null || title.trim().isEmpty()) {
                    continue;
                }
                LocalDate date = Instant.ofEpochMilli(startMillis).atZone(ZoneId.systemDefault()).toLocalDate();
                String key = eventId + ":" + date;
                if (unique.containsKey(key)) {
                    continue;
                }
                DateEvent event = new DateEvent();
                event.id = System.currentTimeMillis() + unique.size();
                event.createdAt = event.id;
                event.externalId = String.valueOf(eventId);
                event.title = title.trim();
                event.type = inferType(event.title);
                event.tagId = TagStore.legacyIdForName(event.type);
                event.calendarType = DateEvent.CALENDAR_SOLAR;
                event.yearKnown = true;
                event.year = date.getYear();
                event.month = date.getMonthValue();
                event.day = date.getDayOfMonth();
                event.yearly = rrule != null && rrule.toUpperCase(Locale.ROOT).contains("FREQ=YEARLY");
                unique.put(key, event);
            }
        }
        return new ArrayList<>(unique.values());
    }

    private static String inferType(String title) {
        if (title.contains("生日")) {
            return DateEvent.TYPE_BIRTHDAY;
        }
        if (title.contains("纪念")) {
            return DateEvent.TYPE_ANNIVERSARY;
        }
        return DateEvent.TYPE_OTHER;
    }
}

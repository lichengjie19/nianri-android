package com.nianri.app;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.CalendarContract;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CalendarExporter {
    private CalendarExporter() {
    }

    public static final class CalendarTarget {
        public final long id;
        public final String displayName;
        public final String accountName;

        CalendarTarget(long id, String displayName, String accountName) {
            this.id = id;
            this.displayName = displayName;
            this.accountName = accountName;
        }

        public String label() {
            if (accountName.isEmpty() || accountName.equals(displayName)) {
                return displayName;
            }
            return displayName + "  ·  " + accountName;
        }
    }

    public static final class ExportResult {
        public int added;
        public int duplicates;
        public int failed;
        public int recurringLunar;
    }

    @SuppressLint("MissingPermission")
    public static List<CalendarTarget> listWritableCalendars(Context context) {
        List<CalendarTarget> result = new ArrayList<>();
        String[] projection = {
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
        };
        String selection = CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";
        String[] args = {String.valueOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)};
        try (Cursor cursor = context.getContentResolver().query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                args,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE ASC"
        )) {
            if (cursor == null) {
                throw new IllegalStateException("无法读取本机日历");
            }
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = clean(cursor.getString(1), "本机日历");
                String account = clean(cursor.getString(2), "");
                result.add(new CalendarTarget(id, name, account));
            }
        }
        return result;
    }

    @SuppressLint("MissingPermission")
    public static synchronized ExportResult export(
            Context context,
            CalendarTarget target,
            List<DateEvent> events
    ) {
        ExportResult result = new ExportResult();
        ContentResolver resolver = context.getContentResolver();
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        // Also cover duplicate selections before the provider refreshes its instance cache.
        Set<CalendarEventKey> existing = new HashSet<>();
        for (DateEvent event : events) {
            try {
                Occurrence occurrence = DateCalculator.occurrence(event, today);
                CalendarEventKey key = new CalendarEventKey(target.id, event.title, occurrence.solarDate);
                long start = ReminderTime.at(occurrence.solarDate, event.reminderHour)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli();
                boolean solarYearly = event.yearly
                        && DateEvent.CALENDAR_SOLAR.equals(event.calendarType);
                if (existing.contains(key) || isDuplicate(resolver, key, zone)) {
                    existing.add(key);
                    result.duplicates++;
                    continue;
                }

                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.CALENDAR_ID, target.id);
                values.put(CalendarContract.Events.TITLE, event.title);
                values.put(CalendarContract.Events.DTSTART, start);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, zone.getId());
                values.put(CalendarContract.Events.DESCRIPTION, description(event, occurrence));
                if (solarYearly) {
                    values.put(CalendarContract.Events.DURATION, "PT1H");
                    values.put(CalendarContract.Events.RRULE, "FREQ=YEARLY");
                } else {
                    values.put(CalendarContract.Events.DTEND, start + 60L * 60L * 1000L);
                }

                ArrayList<ContentProviderOperation> operations = new ArrayList<>();
                operations.add(ContentProviderOperation.newInsert(CalendarContract.Events.CONTENT_URI)
                        .withValues(values)
                        .build());
                if (event.reminderEnabled) {
                    for (int reminderDay : event.reminderDays) {
                        operations.add(ContentProviderOperation.newInsert(CalendarContract.Reminders.CONTENT_URI)
                                .withValueBackReference(CalendarContract.Reminders.EVENT_ID, 0)
                                .withValue(CalendarContract.Reminders.MINUTES, reminderDay * 24 * 60)
                                .withValue(
                                        CalendarContract.Reminders.METHOD,
                                        CalendarContract.Reminders.METHOD_ALERT
                                )
                                .build());
                    }
                }
                resolver.applyBatch(CalendarContract.AUTHORITY, operations);
                existing.add(key);
                result.added++;
                if (event.yearly && DateEvent.CALENDAR_LUNAR.equals(event.calendarType)) {
                    result.recurringLunar++;
                }
            } catch (RemoteException | OperationApplicationException | RuntimeException error) {
                result.failed++;
            }
        }
        return result;
    }

    private static boolean isDuplicate(
            ContentResolver resolver,
            CalendarEventKey key,
            ZoneId zone
    ) {
        String[] projection = {
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.ALL_DAY
        };
        String selection = CalendarContract.Instances.CALENDAR_ID + "=? AND "
                + CalendarContract.Instances.TITLE + "=? AND "
                + CalendarContract.Events.DELETED + "=0 AND ("
                + CalendarContract.Instances.STATUS + " IS NULL OR "
                + CalendarContract.Instances.STATUS + "!=?)";
        String[] args = {
                String.valueOf(key.calendarId), key.title,
                String.valueOf(CalendarContract.Instances.STATUS_CANCELED)
        };
        // Instances expands recurrence rules, so an annual event created in a previous
        // year is still found. Include both local and UTC days for all-day events.
        Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uri, key.queryBegin(zone));
        ContentUris.appendId(uri, key.queryEnd(zone));
        try (Cursor cursor = resolver.query(
                uri.build(),
                projection,
                selection,
                args,
                null
        )) {
            if (cursor == null) {
                // An unavailable provider must never be treated as an empty calendar.
                throw new IllegalStateException("无法检查日历中的已有日程");
            }
            while (cursor.moveToNext()) {
                CalendarEventKey candidate = CalendarEventKey.fromInstance(
                        key.calendarId, key.title, cursor.getLong(0), cursor.getInt(1) != 0, zone
                );
                if (key.equals(candidate)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String description(DateEvent event, Occurrence occurrence) {
        StringBuilder text = new StringBuilder("由念日同步到本机日历\n")
                .append(occurrence.primaryDate)
                .append(" · ")
                .append(occurrence.secondaryDate);
        if (event.yearly && DateEvent.CALENDAR_LUNAR.equals(event.calendarType)) {
            text.append("\n农历年度提醒由念日继续管理；本机日历记录为本次对应的公历日期。");
        }
        if (!event.note.isEmpty()) {
            text.append('\n').append(event.note);
        }
        return text.toString();
    }

    private static String clean(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}

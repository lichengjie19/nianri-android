package com.nianri.app;

import android.annotation.SuppressLint;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.CalendarContract;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

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
                return result;
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
    public static ExportResult export(
            Context context,
            CalendarTarget target,
            List<DateEvent> events
    ) {
        ExportResult result = new ExportResult();
        ContentResolver resolver = context.getContentResolver();
        LocalDate today = LocalDate.now();
        ZoneId zone = ZoneId.systemDefault();
        for (DateEvent event : events) {
            try {
                Occurrence occurrence = DateCalculator.occurrence(event, today);
                long start = ReminderTime.at(occurrence.solarDate, event.reminderHour)
                        .atZone(zone)
                        .toInstant()
                        .toEpochMilli();
                boolean solarYearly = event.yearly
                        && DateEvent.CALENDAR_SOLAR.equals(event.calendarType);
                if (isDuplicate(resolver, target.id, event.title, start)) {
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
            long calendarId,
            String title,
            long start
    ) {
        String[] projection = {CalendarContract.Events._ID};
        String selection = CalendarContract.Events.CALENDAR_ID + "=? AND "
                + CalendarContract.Events.TITLE + "=? AND "
                + CalendarContract.Events.DTSTART + "=? AND "
                + CalendarContract.Events.DELETED + "=0";
        String[] args = {String.valueOf(calendarId), title, String.valueOf(start)};
        try (Cursor cursor = resolver.query(
                CalendarContract.Events.CONTENT_URI,
                projection,
                selection,
                args,
                null
        )) {
            return cursor != null && cursor.moveToFirst();
        }
    }

    private static String description(DateEvent event, Occurrence occurrence) {
        StringBuilder text = new StringBuilder("由念日批量添加\n")
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

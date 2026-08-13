package com.nianri.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlarmManager.AlarmClockInfo;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.provider.Settings;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ReminderScheduler {
    private static final String CHANNEL_SOUND_VIBRATION = "important_date_alarm_sv_v1";
    private static final String CHANNEL_SOUND_ONLY = "important_date_alarm_s_v1";
    private static final String CHANNEL_VIBRATION_ONLY = "important_date_alarm_v_v1";
    private static final String CHANNEL_SILENT = "important_date_alarm_silent_v1";
    private static final String[] ALERT_CHANNEL_IDS = {
            CHANNEL_SOUND_VIBRATION,
            CHANNEL_SOUND_ONLY,
            CHANNEL_VIBRATION_ONLY,
            CHANNEL_SILENT
    };
    private static final String[] LEGACY_CHANNEL_IDS = {
            "important_dates",
            "important_dates_v2"
    };
    private static final String SETTINGS_PREFS = "nianri_settings";
    private static final String KEY_SOUND_ENABLED = "reminder_sound_enabled";
    private static final String KEY_VIBRATION_ENABLED = "reminder_vibration_enabled";
    private static final String PREFS = "nianri_alarm_state";
    private static final String KEY_REQUEST_CODES = "request_codes";
    private static final String KEY_DELIVERED = "delivered_reminders";
    private static final String KEY_MISSED_NOTICE_DATE = "missed_notice_date";
    private static final String KEY_MISSED_NOTICE_KEYS = "missed_notice_keys";
    private static final String KEY_TEST_KIND = "test_kind";
    private static final String KEY_TEST_SCHEDULED_AT = "test_scheduled_at";
    private static final String KEY_TEST_TRIGGER_AT = "test_trigger_at";
    private static final String KEY_TEST_RECEIVED_AT = "test_received_at";
    private static final String KEY_TEST_POSTED_AT = "test_posted_at";
    private static final String KEY_TEST_ERROR = "test_error";
    private static final int TEST_REQUEST_CODE = 0x4e525453;
    private static final int ALARM_INFO_REQUEST_CODE = 0x4e52414c;
    static final String EXTRA_DIAGNOSTIC_TEST = "diagnostic_test";
    static final String EXTRA_FOLLOW_SYSTEM_ALERT = "follow_system_alert";
    static final String EXTRA_ALERT_SOUND = "alert_sound";
    static final String EXTRA_ALERT_VIBRATION = "alert_vibration";

    private ReminderScheduler() {
    }

    public static void ensureNotificationChannel(Context context) {
        ensureNotificationChannel(
                context,
                isReminderSoundEnabled(context),
                isReminderVibrationEnabled(context)
        );
    }

    static void ensureNotificationChannel(
            Context context,
            boolean soundEnabled,
            boolean vibrationEnabled
    ) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        for (String legacyChannelId : LEGACY_CHANNEL_IDS) {
            if (manager.getNotificationChannel(legacyChannelId) != null) {
                manager.deleteNotificationChannel(legacyChannelId);
            }
        }
        String selectedChannelId = alertChannelId(soundEnabled, vibrationEnabled);
        NotificationChannel channel = new NotificationChannel(
                selectedChannelId,
                alertChannelName(soundEnabled, vibrationEnabled),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription("重要日期的顶部通知、系统声音与振动");
        channel.enableVibration(vibrationEnabled);
        if (vibrationEnabled) {
            channel.setVibrationPattern(new long[]{0L, 500L, 240L, 500L, 240L, 700L});
        }
        if (soundEnabled) {
            channel.setSound(
                    Settings.System.DEFAULT_ALARM_ALERT_URI,
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
            );
        } else {
            channel.setSound(null, null);
        }
        channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PRIVATE);
        manager.createNotificationChannel(channel);
    }

    private static String alertChannelName(boolean soundEnabled, boolean vibrationEnabled) {
        if (soundEnabled && vibrationEnabled) return "重要日期提醒（声音和振动）";
        if (soundEnabled) return "重要日期提醒（声音）";
        if (vibrationEnabled) return "重要日期提醒（振动）";
        return "重要日期提醒（静默）";
    }

    public static String notificationChannelId(Context context) {
        return alertChannelId(
                isReminderSoundEnabled(context),
                isReminderVibrationEnabled(context)
        );
    }

    static String notificationChannelId(
            Context context,
            boolean followSystem,
            boolean eventSound,
            boolean eventVibration
    ) {
        return followSystem
                ? notificationChannelId(context)
                : alertChannelId(eventSound, eventVibration);
    }

    static void ensureNotificationChannel(
            Context context,
            boolean followSystem,
            boolean eventSound,
            boolean eventVibration
    ) {
        ensureNotificationChannel(
                context,
                followSystem ? isReminderSoundEnabled(context) : eventSound,
                followSystem ? isReminderVibrationEnabled(context) : eventVibration
        );
    }

    static String alertChannelId(boolean soundEnabled, boolean vibrationEnabled) {
        if (soundEnabled && vibrationEnabled) return CHANNEL_SOUND_VIBRATION;
        if (soundEnabled) return CHANNEL_SOUND_ONLY;
        if (vibrationEnabled) return CHANNEL_VIBRATION_ONLY;
        return CHANNEL_SILENT;
    }

    public static boolean isReminderSoundEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_SOUND_ENABLED, true);
    }

    public static boolean isReminderVibrationEnabled(Context context) {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_VIBRATION_ENABLED, true);
    }

    public static void setReminderSoundEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SOUND_ENABLED, enabled)
                .apply();
        ensureNotificationChannel(context);
    }

    public static void setReminderVibrationEnabled(Context context, boolean enabled) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_VIBRATION_ENABLED, enabled)
                .apply();
        ensureNotificationChannel(context);
    }

    public static synchronized void scheduleAll(Context context) {
        Context appContext = context.getApplicationContext();
        ensureNotificationChannel(appContext);
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        cancelKnownAlarms(appContext, alarmManager);

        LocalDate today = LocalDate.now();
        long nowMillis = System.currentTimeMillis();
        List<Integer> scheduledCodes = new ArrayList<>();
        EventStore store = new EventStore(appContext);
        for (DateEvent event : store.load()) {
            if (!event.reminderEnabled) {
                continue;
            }
            Occurrence occurrence;
            try {
                occurrence = DateCalculator.occurrence(event, today);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (occurrence.expired) {
                continue;
            }
            for (int offset : event.reminderDays) {
                LocalDate occurrenceDate = occurrence.solarDate;
                LocalDate reminderDate = occurrenceDate.minusDays(offset);
                long triggerAt = reminderTimeMillis(
                        reminderDate,
                        event.reminderHour
                );
                if (triggerAt <= nowMillis) {
                    if (!event.yearly) {
                        continue;
                    }
                    LocalDate nextDate = DateCalculator.nextOccurrenceAfter(event, occurrenceDate);
                    reminderDate = nextDate.minusDays(offset);
                    triggerAt = reminderTimeMillis(
                            reminderDate,
                            event.reminderHour
                    );
                    occurrenceDate = nextDate;
                    if (triggerAt <= nowMillis) {
                        continue;
                    }
                }
                int requestCode = requestCode(event.id, offset);
                PendingIntent pendingIntent = reminderPendingIntent(appContext, event, occurrenceDate, offset, requestCode);
                setAlarm(appContext, alarmManager, triggerAt, pendingIntent);
                scheduledCodes.add(requestCode);
            }
        }
        saveScheduledCodes(appContext, scheduledCodes);
    }

    private static void setAlarm(
            Context context,
            AlarmManager manager,
            long triggerAt,
            PendingIntent pendingIntent
    ) {
        if (canScheduleExactAlarms(manager)) {
            try {
                Intent openIntent = new Intent(context, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                PendingIntent showIntent = PendingIntent.getActivity(
                        context,
                        ALARM_INFO_REQUEST_CODE,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                manager.setAlarmClock(new AlarmClockInfo(triggerAt, showIntent), pendingIntent);
                return;
            } catch (SecurityException ignored) {
                // Permission can be revoked between the capability check and this call.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }

    public static boolean canScheduleExactAlarms(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        return canScheduleExactAlarms(manager);
    }

    public static boolean canPostNotifications(Context context) {
        return canPostNotifications(context, notificationChannelId(context));
    }

    static boolean canPostNotifications(Context context, String channelId) {
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (!manager.areNotificationsEnabled()) {
            return false;
        }
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static String notificationImportanceText(Context context) {
        ensureNotificationChannel(context);
        NotificationChannel channel = context.getSystemService(NotificationManager.class)
                .getNotificationChannel(notificationChannelId(context));
        if (channel == null) {
            return "未创建";
        }
        switch (channel.getImportance()) {
            case NotificationManager.IMPORTANCE_HIGH:
            case NotificationManager.IMPORTANCE_MAX:
                return "高优先级";
            case NotificationManager.IMPORTANCE_DEFAULT:
                return "默认优先级";
            case NotificationManager.IMPORTANCE_LOW:
            case NotificationManager.IMPORTANCE_MIN:
                return "静默或低优先级";
            case NotificationManager.IMPORTANCE_NONE:
                return "已关闭";
            default:
                return "由系统管理";
        }
    }

    public static String alertModeText(Context context) {
        ensureNotificationChannel(context);
        NotificationChannel channel = context.getSystemService(NotificationManager.class)
                .getNotificationChannel(notificationChannelId(context));
        if (channel == null) {
            return "未创建";
        }
        boolean sound = channel.getSound() != null;
        boolean vibration = channel.shouldVibrate();
        if (sound && vibration) return "系统闹钟铃声 + 振动";
        if (sound) return "系统闹钟铃声";
        if (vibration) return "仅振动";
        return "静默通知";
    }

    public static synchronized void deliverDueRemindersNow(Context context) {
        Context appContext = context.getApplicationContext();
        LocalDate today = LocalDate.now();
        long nowMillis = System.currentTimeMillis();
        for (DateEvent event : new EventStore(appContext).load()) {
            if (!event.reminderEnabled) {
                continue;
            }
            Occurrence occurrence;
            try {
                occurrence = DateCalculator.occurrence(event, today);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (occurrence.expired) {
                continue;
            }
            for (int offset : event.reminderDays) {
                LocalDate reminderDate = occurrence.solarDate.minusDays(offset);
                long triggerAt = reminderTimeMillis(reminderDate, event.reminderHour);
                boolean delivered = wasDelivered(
                        appContext,
                        event.id,
                        occurrence.solarDate.toEpochDay(),
                        offset,
                        event.reminderHour
                );
                if (!ReminderPolicy.isDueNow(
                        reminderDate,
                        today,
                        triggerAt,
                        nowMillis,
                        delivered
                )) {
                    continue;
                }
                PendingIntent pendingIntent = reminderPendingIntent(
                        appContext,
                        event,
                        occurrence.solarDate,
                        offset,
                        requestCode(event.id, offset)
                );
                try {
                    pendingIntent.send();
                } catch (PendingIntent.CanceledException ignored) {
                    // The normal alarm remains the primary delivery path.
                }
            }
        }
    }

    public static boolean scheduleTestReminder(Context context) {
        Context appContext = context.getApplicationContext();
        ensureNotificationChannel(appContext);
        if (!canPostNotifications(appContext)) {
            return false;
        }
        long scheduledAt = System.currentTimeMillis();
        long triggerAt = scheduledAt + 60_000L;
        resetTestState(appContext, "1 分钟提醒", scheduledAt, triggerAt);
        long eventId = scheduledAt;
        LocalDate today = LocalDate.now();
        Intent intent = testIntent(
                appContext,
                eventId,
                today,
                "1 分钟测试提醒"
        );
        intent.setClass(appContext, ReminderDeliveryService.class);
        PendingIntent pendingIntent = PendingIntent.getForegroundService(
                appContext,
                TEST_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager manager = appContext.getSystemService(AlarmManager.class);
        setAlarm(appContext, manager, triggerAt, pendingIntent);
        return true;
    }

    public static boolean sendImmediateTestNotification(Context context) {
        Context appContext = context.getApplicationContext();
        ensureNotificationChannel(appContext);
        if (!canPostNotifications(appContext)) {
            return false;
        }
        long now = System.currentTimeMillis();
        resetTestState(appContext, "立即通知", now, now);
        appContext.sendBroadcast(testIntent(
                appContext,
                now,
                LocalDate.now(),
                "立即通知测试"
        ));
        return true;
    }

    private static Intent testIntent(
            Context context,
            long eventId,
            LocalDate occurrence,
            String dateText
    ) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction("com.nianri.app.action.TEST_REMINDER");
        intent.putExtra(EXTRA_DIAGNOSTIC_TEST, true);
        intent.putExtra(EXTRA_FOLLOW_SYSTEM_ALERT, true);
        intent.putExtra(EXTRA_ALERT_SOUND, true);
        intent.putExtra(EXTRA_ALERT_VIBRATION, true);
        intent.putExtra("event_id", eventId);
        intent.putExtra("title", "测试提醒");
        intent.putExtra("type", "念日功能测试");
        intent.putExtra("offset", 0);
        intent.putExtra("reminder_hour", LocalDateTime.now().getHour());
        intent.putExtra("occurrence_epoch_day", occurrence.toEpochDay());
        intent.putExtra("date_text", dateText);
        return intent;
    }

    private static void resetTestState(
            Context context,
            String kind,
            long scheduledAt,
            long triggerAt
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEST_KIND, kind)
                .putLong(KEY_TEST_SCHEDULED_AT, scheduledAt)
                .putLong(KEY_TEST_TRIGGER_AT, triggerAt)
                .remove(KEY_TEST_RECEIVED_AT)
                .remove(KEY_TEST_POSTED_AT)
                .remove(KEY_TEST_ERROR)
                .apply();
    }

    static void recordTestReceiverReached(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_TEST_RECEIVED_AT, System.currentTimeMillis())
                .apply();
    }

    static void recordTestNotificationPosted(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putLong(KEY_TEST_POSTED_AT, System.currentTimeMillis())
                .remove(KEY_TEST_ERROR)
                .apply();
    }

    static void recordTestError(Context context, String error) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TEST_ERROR, error)
                .apply();
    }

    public static String testStatusText(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long scheduledAt = preferences.getLong(KEY_TEST_SCHEDULED_AT, 0L);
        if (scheduledAt == 0L) {
            return "测试结果：尚未测试";
        }
        String kind = preferences.getString(KEY_TEST_KIND, "提醒测试");
        long triggerAt = preferences.getLong(KEY_TEST_TRIGGER_AT, scheduledAt);
        long receivedAt = preferences.getLong(KEY_TEST_RECEIVED_AT, 0L);
        long postedAt = preferences.getLong(KEY_TEST_POSTED_AT, 0L);
        String error = preferences.getString(KEY_TEST_ERROR, "");
        if (error != null && !error.isEmpty()) {
            return kind + "：" + error;
        }
        if (postedAt > 0L) {
            return kind + "：应用已提交通知 · " + formatTestTime(postedAt)
                    + "\n若没看到，是手机的通知展示策略拦截。";
        }
        if (receivedAt > 0L) {
            return kind + "：系统已唤醒应用 · " + formatTestTime(receivedAt);
        }
        long now = System.currentTimeMillis();
        if (now <= triggerAt + 20_000L) {
            return kind + "：已登记，预计 " + formatTestTime(triggerAt) + " 到达";
        }
        return kind + "：系统未唤醒应用\n" + backgroundWakeAdvice();
    }

    private static String backgroundWakeAdvice() {
        String manufacturer = Build.MANUFACTURER == null
                ? ""
                : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return "请开启念日的“自启动”，并在“耗电详情”中允许后台高耗电。";
        }
        return "请在手机管家中允许念日自启动和后台运行。";
    }

    private static String formatTestTime(long millis) {
        return java.time.Instant.ofEpochMilli(millis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    private static boolean canScheduleExactAlarms(AlarmManager manager) {
        return Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms();
    }

    private static long reminderTimeMillis(LocalDate date, int hour) {
        LocalDateTime time = ReminderTime.at(date, hour);
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static int requestCode(long eventId, int offset) {
        long value = eventId ^ (eventId >>> 32) ^ (offset * 1103515245L);
        return (int) (value & 0x7fffffff);
    }

    private static PendingIntent reminderPendingIntent(
            Context context,
            DateEvent event,
            LocalDate occurrenceDate,
            int offset,
            int requestCode
    ) {
        Intent intent = new Intent(context, ReminderDeliveryService.class);
        intent.putExtra("event_id", event.id);
        intent.putExtra("title", event.title);
        intent.putExtra("type", event.type);
        intent.putExtra("offset", offset);
        intent.putExtra("reminder_hour", event.reminderHour);
        intent.putExtra("occurrence_epoch_day", occurrenceDate.toEpochDay());
        intent.putExtra("date_text", DateCalculator.fullSolarText(occurrenceDate) + " · " + DateCalculator.lunarText(occurrenceDate));
        intent.putExtra(EXTRA_FOLLOW_SYSTEM_ALERT, event.followSystemAlert);
        intent.putExtra(EXTRA_ALERT_SOUND, event.alertSound);
        intent.putExtra(EXTRA_ALERT_VIBRATION, event.alertVibration);
        return PendingIntent.getForegroundService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    public static synchronized boolean wasDelivered(
            Context context,
            long eventId,
            long occurrenceEpochDay,
            int offset,
            int hour
    ) {
        String key = ReminderPolicy.deliveryKey(eventId, occurrenceEpochDay, offset, hour);
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet(KEY_DELIVERED, Collections.emptySet())
                .contains(key);
    }

    public static synchronized void markDelivered(
            Context context,
            long eventId,
            long occurrenceEpochDay,
            int offset,
            int hour
    ) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> delivered = new HashSet<>(preferences.getStringSet(KEY_DELIVERED, Collections.emptySet()));
        delivered.add(ReminderPolicy.deliveryKey(eventId, occurrenceEpochDay, offset, hour));
        preferences.edit().putStringSet(KEY_DELIVERED, delivered).apply();
    }

    public static synchronized List<MissedReminder> findUnacknowledgedMissedToday(Context context) {
        Context appContext = context.getApplicationContext();
        LocalDate today = LocalDate.now();
        long nowMillis = System.currentTimeMillis();
        SharedPreferences preferences = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> acknowledged = preferences.getLong(KEY_MISSED_NOTICE_DATE, Long.MIN_VALUE)
                == today.toEpochDay()
                ? new HashSet<>(preferences.getStringSet(KEY_MISSED_NOTICE_KEYS, Collections.emptySet()))
                : new HashSet<>();

        List<MissedReminder> result = new ArrayList<>();
        for (DateEvent event : new EventStore(appContext).load()) {
            if (!event.reminderEnabled) {
                continue;
            }
            Occurrence occurrence;
            try {
                occurrence = DateCalculator.occurrence(event, today);
            } catch (RuntimeException ignored) {
                continue;
            }
            if (occurrence.expired) {
                continue;
            }
            for (int offset : event.reminderDays) {
                LocalDate reminderDate = occurrence.solarDate.minusDays(offset);
                long triggerAt = reminderTimeMillis(reminderDate, event.reminderHour);
                String key = ReminderPolicy.deliveryKey(
                        event.id,
                        occurrence.solarDate.toEpochDay(),
                        offset,
                        event.reminderHour
                );
                boolean delivered = wasDelivered(
                        appContext,
                        event.id,
                        occurrence.solarDate.toEpochDay(),
                        offset,
                        event.reminderHour
                );
                if (ReminderPolicy.isMissedToday(
                        reminderDate,
                        today,
                        triggerAt,
                        nowMillis,
                        delivered
                ) && !acknowledged.contains(key)) {
                    result.add(new MissedReminder(key, event.title, offset, event.reminderHour));
                }
            }
        }
        return result;
    }

    public static synchronized void acknowledgeMissedToday(
            Context context,
            List<MissedReminder> reminders
    ) {
        if (reminders.isEmpty()) {
            return;
        }
        SharedPreferences preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        LocalDate today = LocalDate.now();
        Set<String> acknowledged = preferences.getLong(KEY_MISSED_NOTICE_DATE, Long.MIN_VALUE)
                == today.toEpochDay()
                ? new HashSet<>(preferences.getStringSet(KEY_MISSED_NOTICE_KEYS, Collections.emptySet()))
                : new HashSet<>();
        for (MissedReminder reminder : reminders) {
            acknowledged.add(reminder.key);
        }
        preferences.edit()
                .putLong(KEY_MISSED_NOTICE_DATE, today.toEpochDay())
                .putStringSet(KEY_MISSED_NOTICE_KEYS, acknowledged)
                .apply();
    }

    public static final class MissedReminder {
        private final String key;
        public final String title;
        public final int offset;
        public final int hour;

        private MissedReminder(String key, String title, int offset, int hour) {
            this.key = key;
            this.title = title;
            this.offset = offset;
            this.hour = ReminderTime.normalizeHour(hour);
        }
    }

    private static void cancelKnownAlarms(Context context, AlarmManager manager) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_REQUEST_CODES, "");
        if (raw == null || raw.isEmpty()) {
            return;
        }
        String[] parts = raw.split(",");
        for (String part : parts) {
            try {
                int requestCode = Integer.parseInt(part);
                Intent serviceIntent = new Intent(context, ReminderDeliveryService.class);
                PendingIntent pendingIntent = PendingIntent.getForegroundService(
                        context,
                        requestCode,
                        serviceIntent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (pendingIntent != null) {
                    manager.cancel(pendingIntent);
                    pendingIntent.cancel();
                }
                Intent legacyIntent = new Intent(context, ReminderReceiver.class);
                PendingIntent legacyPendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        legacyIntent,
                        PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
                );
                if (legacyPendingIntent != null) {
                    manager.cancel(legacyPendingIntent);
                    legacyPendingIntent.cancel();
                }
            } catch (NumberFormatException ignored) {
                // Ignore stale state from an older build.
            }
        }
    }

    private static void saveScheduledCodes(Context context, List<Integer> codes) {
        StringBuilder value = new StringBuilder();
        for (int code : codes) {
            if (value.length() > 0) {
                value.append(',');
            }
            value.append(code);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_REQUEST_CODES, value.toString())
                .apply();
    }
}

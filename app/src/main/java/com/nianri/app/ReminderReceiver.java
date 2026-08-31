package com.nianri.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        DateWidgetProvider.refreshAll(context);
        boolean diagnosticTest = intent.getBooleanExtra(
                ReminderScheduler.EXTRA_DIAGNOSTIC_TEST,
                false
        );
        boolean followSystemAlert = intent.getBooleanExtra(
                ReminderScheduler.EXTRA_FOLLOW_SYSTEM_ALERT,
                true
        );
        boolean eventSound = intent.getBooleanExtra(ReminderScheduler.EXTRA_ALERT_SOUND, true);
        boolean eventVibration = intent.getBooleanExtra(
                ReminderScheduler.EXTRA_ALERT_VIBRATION,
                true
        );
        if (diagnosticTest) {
            ReminderScheduler.recordTestReceiverReached(context);
        }
        ReminderScheduler.ensureNotificationChannel(
                context,
                followSystemAlert,
                eventSound,
                eventVibration
        );
        String channelId = ReminderScheduler.notificationChannelId(
                context,
                followSystemAlert,
                eventSound,
                eventVibration
        );
        if (!ReminderScheduler.canPostNotifications(context, channelId)) {
            if (diagnosticTest) {
                ReminderScheduler.recordTestError(context, "通知权限或通知频道已被系统关闭");
            }
            return;
        }

        String title = intent.getStringExtra("title");
        String type = intent.getStringExtra("type");
        String dateText = intent.getStringExtra("date_text");
        long epochDay = intent.getLongExtra("occurrence_epoch_day", LocalDate.now().toEpochDay());
        long eventId = intent.getLongExtra("event_id", System.currentTimeMillis());
        int offset = intent.getIntExtra("offset", 0);
        int reminderHour = intent.getIntExtra("reminder_hour", ReminderTime.DEFAULT_HOUR);
        if (ReminderScheduler.wasDelivered(context, eventId, epochDay, offset, reminderHour)) {
            ReminderScheduler.scheduleAll(context);
            return;
        }
        LocalDate occurrence = LocalDate.ofEpochDay(epochDay);
        long remaining = Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), occurrence));

        String notificationTitle = remaining == 0
                ? "今天是“" + title + "”"
                : "距离“" + title + "”还有 " + remaining + " 天";
        String content = (type == null ? "重要日期" : type) + " · " + (dateText == null ? "" : dateText);

        int notificationId = (int) ((eventId ^ epochDay) & 0x7fffffff);
        Intent openIntent = new Intent(context, MainActivity.class);
        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                (int) (eventId & 0x7fffffff),
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = new Notification.Builder(context, channelId);
        builder.setSmallIcon(R.drawable.ic_notification)
                .setColor(Color.parseColor("#DF695D"))
                .setContentTitle(notificationTitle)
                .setContentText(content)
                .setStyle(new Notification.BigTextStyle().bigText(content))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOngoing(false)
                .setPriority(Notification.PRIORITY_HIGH)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PRIVATE);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            manager.notify(notificationId, builder.build());
        } catch (RuntimeException error) {
            if (diagnosticTest) {
                ReminderScheduler.recordTestError(
                        context,
                        "提交通知失败：" + error.getClass().getSimpleName()
                );
            }
            return;
        }
        if (diagnosticTest) {
            ReminderScheduler.recordTestNotificationPosted(context);
        }
        ReminderScheduler.markDelivered(context, eventId, epochDay, offset, reminderHour);
        ReminderScheduler.scheduleAll(context);
    }
}

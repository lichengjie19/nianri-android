package com.nianri.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class ReminderDeliveryService extends Service {
    private static final String DELIVERY_CHANNEL_ID = "reminder_delivery_service";
    private static final int FOREGROUND_NOTIFICATION_ID = 0x4e524647;

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(
                DELIVERY_CHANNEL_ID,
                "提醒送达服务",
                NotificationManager.IMPORTANCE_MIN
        );
        channel.setDescription("仅在提醒到点时短暂运行");
        channel.setSound(null, null);
        channel.enableVibration(false);
        channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent openIntent = new Intent(this, MainActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                this,
                FOREGROUND_NOTIFICATION_ID,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String title = intent == null ? null : intent.getStringExtra("title");
        Notification foreground = new Notification.Builder(this, DELIVERY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("正在送达提醒")
                .setContentText(title == null || title.isEmpty() ? "念日" : title)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOnlyAlertOnce(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(
                    FOREGROUND_NOTIFICATION_ID,
                    foreground,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
            );
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, foreground);
        }

        try {
            if (intent != null) {
                intent.setClass(this, ReminderReceiver.class);
                new ReminderReceiver().onReceive(this, intent);
            }
        } catch (RuntimeException error) {
            if (intent != null && intent.getBooleanExtra(
                    ReminderScheduler.EXTRA_DIAGNOSTIC_TEST,
                    false
            )) {
                ReminderScheduler.recordTestError(
                        this,
                        "后台送达失败：" + error.getClass().getSimpleName()
                );
            }
        } finally {
            stopForeground(STOP_FOREGROUND_REMOVE);
            stopSelf(startId);
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

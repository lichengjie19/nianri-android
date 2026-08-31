package com.nianri.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public final class DateWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "nianri_date_widgets";
    private static final String KEY_EVENT_PREFIX = "event_id_";
    private static final String ACTION_REFRESH_FOR_NEW_DAY =
            "com.nianri.app.action.REFRESH_DATE_WIDGETS_FOR_NEW_DAY";
    private static final int REFRESH_REQUEST_CODE = 0x4e525744;

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            update(context, manager, appWidgetId);
        }
        scheduleNextDayRefresh(context);
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        scheduleNextDayRefresh(context);
    }

    @Override
    public void onDisabled(Context context) {
        cancelNextDayRefresh(context);
        super.onDisabled(context);
    }

    @Override
    public void onAppWidgetOptionsChanged(
            Context context,
            AppWidgetManager manager,
            int appWidgetId,
            Bundle newOptions
    ) {
        update(context, manager, appWidgetId);
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        SharedPreferences.Editor editor = preferences(context).edit();
        for (int appWidgetId : appWidgetIds) {
            editor.remove(key(appWidgetId));
        }
        editor.apply();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || ACTION_REFRESH_FOR_NEW_DAY.equals(action)) {
            refreshAll(context);
        }
    }

    static void saveSelection(Context context, int appWidgetId, long eventId) {
        preferences(context).edit().putLong(key(appWidgetId), eventId).apply();
    }

    static long loadSelection(Context context, int appWidgetId) {
        return preferences(context).getLong(key(appWidgetId), DateWidgetModel.AUTO_EVENT_ID);
    }

    static boolean hasSavedSelection(Context context, int appWidgetId) {
        return preferences(context).contains(key(appWidgetId));
    }

    static int[] activeWidgetIds(Context context) {
        Context appContext = context.getApplicationContext();
        return AppWidgetManager.getInstance(appContext).getAppWidgetIds(
                new ComponentName(appContext, DateWidgetProvider.class)
        );
    }

    static void update(Context context, AppWidgetManager manager, int appWidgetId) {
        List<DateEvent> events = new EventStore(context).load();
        long selectedEventId = loadSelection(context, appWidgetId);
        LocalDate today = LocalDate.now();
        DateEvent event = DateWidgetModel.resolveEvent(events, selectedEventId, today);
        boolean followsNearest = selectedEventId == DateWidgetModel.AUTO_EVENT_ID;
        if (!followsNearest && (event == null || event.id != selectedEventId)) {
            selectedEventId = DateWidgetModel.AUTO_EVENT_ID;
            followsNearest = true;
            saveSelection(context, appWidgetId, selectedEventId);
        }
        DateWidgetModel.CardContent content;
        try {
            content = DateWidgetModel.content(event, today);
        } catch (RuntimeException ignored) {
            event = null;
            content = DateWidgetModel.content(null, today);
        }

        RemoteViews views = responsiveViews(
                context,
                appWidgetId,
                event,
                content,
                followsNearest
        );
        manager.updateAppWidget(appWidgetId, views);
    }

    private static RemoteViews responsiveViews(
            Context context,
            int appWidgetId,
            DateEvent event,
            DateWidgetModel.CardContent content,
            boolean followsNearest
    ) {
        return createViews(
                context,
                R.layout.date_widget_extreme,
                appWidgetId,
                event,
                content,
                followsNearest
        );
    }

    private static RemoteViews createViews(
            Context context,
            int layoutResource,
            int appWidgetId,
            DateEvent event,
            DateWidgetModel.CardContent content,
            boolean followsNearest
    ) {
        RemoteViews views = new RemoteViews(context.getPackageName(), layoutResource);
        views.setTextViewText(
                R.id.widget_kicker,
                context.getString(followsNearest
                        ? R.string.date_widget_kicker_nearest
                        : R.string.date_widget_kicker_specific)
        );
        views.setTextViewText(R.id.widget_title, content.title);
        views.setTextViewText(R.id.widget_number, content.number);
        views.setTextViewTextSize(
                R.id.widget_number,
                TypedValue.COMPLEX_UNIT_SP,
                DateWidgetLayout.countdownTextSizeSp(content.number)
        );
        views.setTextViewText(R.id.widget_unit, content.unit);
        views.setViewVisibility(R.id.widget_unit, content.unit.isEmpty() ? View.GONE : View.VISIBLE);
        views.setTextViewText(R.id.widget_date, content.date);
        views.setTextViewText(R.id.widget_detail, content.detail);
        views.setViewVisibility(
                R.id.widget_detail,
                content.detail.isEmpty() ? View.GONE : View.VISIBLE
        );
        views.setContentDescription(R.id.widget_root, content.contentDescription);
        views.setOnClickPendingIntent(
                R.id.widget_root,
                openPendingIntent(context, appWidgetId, event)
        );
        return views;
    }

    public static void refreshAll(Context context) {
        Context appContext = context.getApplicationContext();
        AppWidgetManager manager = AppWidgetManager.getInstance(appContext);
        int[] appWidgetIds = activeWidgetIds(appContext);
        for (int appWidgetId : appWidgetIds) {
            update(appContext, manager, appWidgetId);
        }
        if (appWidgetIds.length == 0) {
            cancelNextDayRefresh(appContext);
        } else {
            scheduleNextDayRefresh(appContext);
        }
    }

    private static void scheduleNextDayRefresh(Context context) {
        Context appContext = context.getApplicationContext();
        if (activeWidgetIds(appContext).length == 0) {
            cancelNextDayRefresh(appContext);
            return;
        }
        AlarmManager manager = appContext.getSystemService(AlarmManager.class);
        PendingIntent pendingIntent = refreshPendingIntent(
                appContext,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        long triggerAt = DateWidgetRefreshSchedule.nextTriggerAtMillis(
                System.currentTimeMillis(),
                ZoneId.systemDefault()
        );
        if (ReminderScheduler.canScheduleExactAlarms(appContext)) {
            try {
                manager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAt,
                        pendingIntent
                );
                return;
            } catch (SecurityException ignored) {
                // Permission can be revoked between the capability check and this call.
            }
        }
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
    }

    private static void cancelNextDayRefresh(Context context) {
        Context appContext = context.getApplicationContext();
        PendingIntent pendingIntent = refreshPendingIntent(
                appContext,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent == null) {
            return;
        }
        AlarmManager manager = appContext.getSystemService(AlarmManager.class);
        manager.cancel(pendingIntent);
        pendingIntent.cancel();
    }

    private static PendingIntent refreshPendingIntent(Context context, int flags) {
        Intent intent = new Intent(context, DateWidgetProvider.class)
                .setAction(ACTION_REFRESH_FOR_NEW_DAY);
        return PendingIntent.getBroadcast(context, REFRESH_REQUEST_CODE, intent, flags);
    }

    private static PendingIntent openPendingIntent(
            Context context,
            int appWidgetId,
            DateEvent event
    ) {
        Intent intent;
        if (event == null) {
            intent = new Intent(context, MainActivity.class);
        } else {
            intent = new Intent(context, DateEditorActivity.class);
            intent.putExtra(DateEditorActivity.EXTRA_EVENT_ID, event.id);
        }
        long eventId = event == null ? 0L : event.id;
        intent.setData(Uri.parse("nianri://widget/" + appWidgetId + "/event/" + eventId));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String key(int appWidgetId) {
        return KEY_EVENT_PREFIX + appWidgetId;
    }
}

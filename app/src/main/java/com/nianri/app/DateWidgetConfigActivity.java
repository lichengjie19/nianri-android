package com.nianri.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class DateWidgetConfigActivity extends Activity {
    private static final int SELECTED_SURFACE = Color.rgb(255, 239, 237);
    private static final String STATE_SELECTED_EVENT_ID = "selected_event_id";

    private final Map<Long, TextView> markers = new HashMap<>();
    private final Map<Long, LinearLayout> choiceCards = new HashMap<>();
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private long selectedEventId = DateWidgetModel.AUTO_EVENT_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
            );
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        if (!DateWidgetInstancePolicy.canConfigure(
                appWidgetId,
                DateWidgetProvider.activeWidgetIds(this),
                DateWidgetProvider.hasSavedSelection(this, appWidgetId)
        )) {
            Toast.makeText(
                    this,
                    R.string.date_widget_already_added,
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        selectedEventId = savedInstanceState == null
                ? DateWidgetProvider.loadSelection(this, appWidgetId)
                : savedInstanceState.getLong(
                        STATE_SELECTED_EVENT_ID,
                        DateWidgetModel.AUTO_EVENT_ID
                );
        List<DateEvent> events = sortedEvents(new EventStore(this).load());
        if (selectedEventId != DateWidgetModel.AUTO_EVENT_ID && !contains(events, selectedEventId)) {
            selectedEventId = DateWidgetModel.AUTO_EVENT_ID;
        }
        setContentView(buildPage(events));
        updateChoices();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(STATE_SELECTED_EVENT_ID, selectedEventId);
        super.onSaveInstanceState(outState);
    }

    private View buildPage(List<DateEvent> events) {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        int side = Ui.dp(this, 20);
        root.setPadding(side, Ui.dp(this, 16), side, Ui.dp(this, 18));

        LinearLayout header = Ui.horizontal(this);
        TextView cancel = Ui.text(this, "取消", 15, Ui.MUTED, false);
        cancel.setGravity(Gravity.CENTER);
        cancel.setClickable(true);
        cancel.setFocusable(true);
        cancel.setOnClickListener(view -> finish());
        header.addView(cancel, Ui.linearParams(Ui.dp(this, 54), Ui.dp(this, 44)));

        TextView title = Ui.text(this, "添加桌面卡片", 20, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        header.addView(title, Ui.weightedParams(Ui.dp(this, 44), 1));
        header.addView(new View(this), Ui.linearParams(Ui.dp(this, 54), Ui.dp(this, 44)));
        root.addView(header, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        LinearLayout content = Ui.vertical(this);
        content.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 18));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView hint = Ui.text(
                this,
                "选择卡片要展示的日期。卡片会自动更新，点击后可直接打开日期详情。",
                14,
                Ui.MUTED,
                false
        );
        hint.setLineSpacing(0, 1.2f);
        content.addView(hint, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        addChoice(
                content,
                DateWidgetModel.AUTO_EVENT_ID,
                "自动显示最近日期",
                "始终展示最近一个即将到来的重要日期"
        );

        if (events.isEmpty()) {
            TextView empty = Ui.text(this, "还没有可选择的日期，仍可使用自动卡片。", 13, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams emptyParams = Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            emptyParams.topMargin = Ui.dp(this, 24);
            content.addView(empty, emptyParams);
        } else {
            TextView section = Ui.text(this, "指定日期", 13, Ui.MUTED, true);
            LinearLayout.LayoutParams sectionParams = Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            sectionParams.topMargin = Ui.dp(this, 28);
            sectionParams.bottomMargin = Ui.dp(this, 10);
            content.addView(section, sectionParams);
            for (DateEvent event : events) {
                addChoice(content, event.id, displayTitle(event), summary(event));
            }
        }

        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1
        );
        root.addView(scroll, scrollParams);

        TextView add = Ui.button(this, "添加到桌面", Color.WHITE, Ui.ACCENT, 18);
        add.setOnClickListener(view -> finishConfiguration());
        root.addView(add, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 52)
        ));
        return root;
    }

    private void addChoice(
            LinearLayout parent,
            long eventId,
            String title,
            String detail
    ) {
        LinearLayout card = Ui.horizontal(this);
        card.setPadding(
                Ui.dp(this, 15),
                Ui.dp(this, 14),
                Ui.dp(this, 15),
                Ui.dp(this, 14)
        );
        card.setClickable(true);
        card.setFocusable(true);
        card.setOnClickListener(view -> {
            selectedEventId = eventId;
            updateChoices();
        });

        TextView marker = Ui.text(this, "", 13, Color.WHITE, true);
        marker.setGravity(Gravity.CENTER);
        card.addView(marker, Ui.linearParams(Ui.dp(this, 24), Ui.dp(this, 24)));

        LinearLayout labels = Ui.vertical(this);
        TextView name = Ui.text(this, title, 15, Ui.TEXT, true);
        name.setMaxLines(1);
        TextView description = Ui.text(this, detail, 12, Ui.MUTED, false);
        description.setMaxLines(2);
        LinearLayout.LayoutParams descriptionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = Ui.dp(this, 5);
        labels.addView(name, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        labels.addView(description, descriptionParams);
        LinearLayout.LayoutParams labelParams = Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        labelParams.leftMargin = Ui.dp(this, 12);
        card.addView(labels, labelParams);

        LinearLayout.LayoutParams cardParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.topMargin = Ui.dp(this, 10);
        parent.addView(card, cardParams);
        markers.put(eventId, marker);
        choiceCards.put(eventId, card);
    }

    private void updateChoices() {
        for (Map.Entry<Long, TextView> entry : markers.entrySet()) {
            boolean selected = entry.getKey() == selectedEventId;
            TextView marker = entry.getValue();
            marker.setText(selected ? "✓" : "");
            marker.setBackground(selected
                    ? Ui.rounded(this, Ui.ACCENT, 12)
                    : Ui.roundedStroke(this, Ui.SURFACE, 12, Ui.BORDER, 1));
            LinearLayout card = choiceCards.get(entry.getKey());
            if (card != null) {
                card.setBackground(Ui.roundedStroke(
                        this,
                        selected ? SELECTED_SURFACE : Ui.SURFACE,
                        18,
                        selected ? Ui.ACCENT : Ui.BORDER,
                        1
                ));
            }
        }
    }

    private void finishConfiguration() {
        DateWidgetProvider.saveSelection(this, appWidgetId, selectedEventId);
        DateWidgetProvider.update(
                this,
                AppWidgetManager.getInstance(this),
                appWidgetId
        );
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private static List<DateEvent> sortedEvents(List<DateEvent> source) {
        List<DateEvent> events = new ArrayList<>(source);
        LocalDate today = LocalDate.now();
        events.sort(Comparator
                .comparingInt((DateEvent event) -> isExpired(event, today) ? 1 : 0)
                .thenComparingLong(event -> distance(event, today))
                .thenComparingLong(event -> event.createdAt));
        return events;
    }

    private static boolean isExpired(DateEvent event, LocalDate today) {
        try {
            return DateCalculator.occurrence(event, today).expired;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    private static long distance(DateEvent event, LocalDate today) {
        try {
            return Math.abs(DateCalculator.occurrence(event, today).daysFromToday);
        } catch (RuntimeException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static String summary(DateEvent event) {
        try {
            Occurrence occurrence = DateCalculator.occurrence(event, LocalDate.now());
            long days = occurrence.daysFromToday;
            String timing;
            if (days < 0) {
                timing = "已过 " + Math.abs(days) + " 天";
            } else if (days == 0) {
                timing = "今天";
            } else {
                timing = "还有 " + days + " 天";
            }
            return DateCalculator.fullSolarText(occurrence.solarDate) + " · " + timing;
        } catch (RuntimeException ignored) {
            return "日期信息有误";
        }
    }

    private static String displayTitle(DateEvent event) {
        return event.title == null || event.title.trim().isEmpty()
                ? "重要日期"
                : event.title.trim();
    }

    private static boolean contains(List<DateEvent> events, long eventId) {
        for (DateEvent event : events) {
            if (event.id == eventId) {
                return true;
            }
        }
        return false;
    }
}

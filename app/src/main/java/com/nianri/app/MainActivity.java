package com.nianri.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MainActivity extends Activity {
    private static final String FILTER_ALL = "__all__";
    private static final int REQUEST_NOTIFICATIONS = 1001;
    private static final int REQUEST_CALENDAR = 1002;
    private static final int REQUEST_CALENDAR_EXPORT = 1003;

    private EventStore store;
    private TagStore tagStore;
    private LinearLayout filterContainer;
    private LinearLayout activeList;
    private TextView emptyView;
    private TextView summaryTitle;
    private TextView summaryCount;
    private LinearLayout summaryCard;
    private View archiveScrim;
    private LinearLayout archiveSidebar;
    private TextView expiredArchiveCount;
    private TextView deletedArchiveCount;
    private boolean archiveSidebarOpen;
    private String currentFilter = FILTER_ALL;
    private final Map<String, TextView> filterButtons = new HashMap<>();
    private final List<DateEvent> pendingCalendarExports = new ArrayList<>();
    private final Handler minuteHandler = new Handler(Looper.getMainLooper());
    private final Runnable minuteRefresh = new Runnable() {
        @Override
        public void run() {
            if (activeList != null) {
                ReminderScheduler.deliverDueRemindersNow(MainActivity.this);
                renderEvents();
                scheduleMinuteRefresh();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        store = new EventStore(this);
        tagStore = new TagStore(this);
        store.seedDemoDatesIfNeeded();
        setContentView(buildHome());
        registerPredictiveBackIfSupported();
        renderEvents();
        ReminderScheduler.scheduleAll(this);
        boolean notificationPermissionRequested = requestNotificationPermissionIfNeeded();
        if (!notificationPermissionRequested) {
            getWindow().getDecorView().post(this::maybeRequestExactAlarmPermissionIfNeeded);
        }
        maybeAutoCheckAuthoritySources();
    }

    @Override
    protected void onResume() {
        super.onResume();
        DateWidgetProvider.refreshAll(this);
        if (activeList != null) {
            rebuildFilterButtons();
            ReminderScheduler.deliverDueRemindersNow(this);
            renderEvents();
            scheduleMinuteRefresh();
            showMissedReminderNoticeIfNeeded();
            ReminderScheduler.scheduleAll(this);
        }
    }

    @Override
    protected void onPause() {
        minuteHandler.removeCallbacks(minuteRefresh);
        super.onPause();
    }

    private void scheduleMinuteRefresh() {
        minuteHandler.removeCallbacks(minuteRefresh);
        long delay = 60_000L - (System.currentTimeMillis() % 60_000L) + 50L;
        minuteHandler.postDelayed(minuteRefresh, delay);
    }

    private void showMissedReminderNoticeIfNeeded() {
        List<ReminderScheduler.MissedReminder> missed =
                ReminderScheduler.findUnacknowledgedMissedToday(this);
        if (missed.isEmpty()) {
            return;
        }
        ReminderScheduler.acknowledgeMissedToday(this, missed);
        String message;
        if (missed.size() == 1) {
            ReminderScheduler.MissedReminder reminder = missed.get(0);
            String timing = reminder.offset == 0
                    ? "当天"
                    : "提前" + reminder.offset + "天";
            message = "“" + reminder.title + "”的"
                    + timing + " " + ReminderTime.format(reminder.hour)
                    + " 提醒未送达";
        } else {
            StringBuilder titles = new StringBuilder();
            for (int i = 0; i < Math.min(3, missed.size()); i++) {
                if (titles.length() > 0) titles.append("、");
                titles.append(missed.get(i).title);
            }
            if (missed.size() > 3) titles.append("等");
            message = "今天有 " + missed.size() + " 个提醒未送达：" + titles;
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private View buildHome() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BACKGROUND);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        LinearLayout content = Ui.vertical(this);
        int side = Ui.dp(this, 20);
        content.setPadding(side, Ui.dp(this, 18), side, Ui.dp(this, 104));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout header = Ui.horizontal(this);
        LinearLayout titles = Ui.vertical(this);
        titles.setClickable(true);
        titles.setFocusable(true);
        titles.setContentDescription("念日，打开设置");
        titles.setOnClickListener(view -> showSettings());
        LinearLayout titleLine = Ui.horizontal(this);
        TextView appTitle = Ui.text(this, "念日", 28, Ui.TEXT, true);
        titleLine.addView(appTitle, Ui.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView settingsHint = Ui.text(this, "⚙", 12, Ui.MUTED, false);
        settingsHint.setGravity(Gravity.CENTER);
        settingsHint.setBackground(Ui.rounded(this, Color.rgb(236, 234, 228), 999));
        LinearLayout.LayoutParams settingsHintParams = Ui.linearParams(Ui.dp(this, 22), Ui.dp(this, 22));
        settingsHintParams.leftMargin = Ui.dp(this, 7);
        titleLine.addView(settingsHint, settingsHintParams);
        TextView subtitle = Ui.text(this, "重要的日子，不再错过", 13, Ui.MUTED, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titles.addView(titleLine, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams subtitleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = Ui.dp(this, 6);
        titles.addView(subtitle, subtitleParams);
        header.addView(titles, Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView batch = Ui.button(this, "批量", Ui.TEXT, Color.rgb(236, 234, 228), 14);
        batch.setContentDescription("批量添加到本机日历");
        batch.setOnClickListener(view -> showCalendarExportPicker());
        LinearLayout.LayoutParams batchParams = Ui.linearParams(Ui.dp(this, 58), Ui.dp(this, 40));
        batchParams.rightMargin = Ui.dp(this, 8);
        header.addView(batch, batchParams);
        TextView storage = Ui.button(this, "收纳", Ui.TEXT, Color.rgb(236, 234, 228), 14);
        storage.setContentDescription("打开日期收纳");
        storage.setOnClickListener(view -> openArchiveSidebar());
        header.addView(storage, Ui.linearParams(Ui.dp(this, 58), Ui.dp(this, 40)));
        content.addView(header, Ui.linearParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        summaryCard = Ui.horizontal(this);
        summaryCard.setPadding(Ui.dp(this, 17), Ui.dp(this, 15), Ui.dp(this, 17), Ui.dp(this, 15));
        summaryCard.setBackground(Ui.roundedStroke(
                this,
                Ui.GREEN_SURFACE,
                21,
                Color.rgb(212, 229, 217),
                1
        ));
        LinearLayout summaryText = Ui.vertical(this);
        TextView summaryKicker = Ui.text(this, "最近一件", 12, Color.rgb(86, 129, 100), true);
        summaryTitle = Ui.text(this, "", 16, Ui.TEXT, true);
        summaryText.addView(summaryKicker);
        LinearLayout.LayoutParams titleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = Ui.dp(this, 5);
        summaryText.addView(summaryTitle, titleParams);
        summaryCard.addView(summaryText, Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        summaryCount = Ui.text(this, "", 24, Ui.GREEN_TEXT, true);
        summaryCount.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        summaryCard.addView(summaryCount, Ui.linearParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 54)));
        LinearLayout.LayoutParams summaryParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryParams.topMargin = Ui.dp(this, 22);
        content.addView(summaryCard, summaryParams);

        HorizontalScrollView filterScroll = new HorizontalScrollView(this);
        filterScroll.setHorizontalScrollBarEnabled(false);
        filterContainer = Ui.horizontal(this);
        filterScroll.addView(filterContainer, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        rebuildFilterButtons();
        LinearLayout.LayoutParams filterParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        filterParams.topMargin = Ui.dp(this, 18);
        content.addView(filterScroll, filterParams);

        activeList = Ui.vertical(this);
        LinearLayout.LayoutParams listParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        listParams.topMargin = Ui.dp(this, 14);
        content.addView(activeList, listParams);

        emptyView = Ui.text(this, "这个分类还没有日期", 13, Ui.MUTED, false);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setPadding(0, Ui.dp(this, 30), 0, Ui.dp(this, 30));
        content.addView(emptyView, Ui.linearParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView fab = Ui.button(this, "＋", Color.WHITE, Ui.ACCENT, 20);
        fab.setTextSize(28);
        fab.setContentDescription("添加重要日期");
        fab.setOnClickListener(view -> {
            Intent intent = new Intent(this, DateEditorActivity.class);
            intent.putExtra(DateEditorActivity.EXTRA_DEFAULT_TAG_ID, currentFilter);
            startActivity(intent);
        });
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(Ui.dp(this, 60), Ui.dp(this, 60));
        fabParams.gravity = Gravity.BOTTOM | Gravity.END;
        fabParams.setMargins(0, 0, Ui.dp(this, 22), Ui.dp(this, 24));
        root.addView(fab, fabParams);

        archiveScrim = new View(this);
        archiveScrim.setBackgroundColor(Color.argb(108, 30, 29, 27));
        archiveScrim.setAlpha(0f);
        archiveScrim.setVisibility(View.GONE);
        archiveScrim.setOnClickListener(view -> closeArchiveSidebar());
        root.addView(archiveScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        archiveSidebar = buildArchiveSidebar();
        int sidebarWidth = Math.min(
                Ui.dp(this, 310),
                Math.round(getResources().getDisplayMetrics().widthPixels * 0.84f)
        );
        FrameLayout.LayoutParams sidebarParams = new FrameLayout.LayoutParams(
                sidebarWidth,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        sidebarParams.gravity = Gravity.END;
        archiveSidebar.setTranslationX(sidebarWidth);
        archiveSidebar.setVisibility(View.GONE);
        root.addView(archiveSidebar, sidebarParams);

        selectFilter(FILTER_ALL);
        return root;
    }

    private LinearLayout buildArchiveSidebar() {
        LinearLayout panel = Ui.vertical(this);
        panel.setPadding(
                Ui.dp(this, 20),
                Ui.dp(this, 30),
                Ui.dp(this, 20),
                Ui.dp(this, 24)
        );
        panel.setBackgroundColor(Ui.BACKGROUND);
        panel.setElevation(Ui.dp(this, 18));

        LinearLayout heading = Ui.horizontal(this);
        LinearLayout headingText = Ui.vertical(this);
        TextView title = Ui.text(this, "日期收纳", 22, Ui.TEXT, true);
        TextView subtitle = Ui.text(this, "查看不再显示在首页的日期", 12, Ui.MUTED, false);
        headingText.addView(title, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams subtitleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = Ui.dp(this, 7);
        headingText.addView(subtitle, subtitleParams);
        heading.addView(headingText, Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ImageButton close = new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setBackground(Ui.ripple(this, Color.rgb(236, 234, 228), 999));
        close.setPadding(
                Ui.dp(this, 10),
                Ui.dp(this, 10),
                Ui.dp(this, 10),
                Ui.dp(this, 10)
        );
        close.setContentDescription("关闭日期收纳");
        close.setOnClickListener(view -> closeArchiveSidebar());
        heading.addView(close, Ui.linearParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        panel.addView(heading, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout expired = archiveSidebarItem(
                "⌛",
                "已结束",
                "已经结束的单次日期",
                true
        );
        expired.setOnClickListener(view -> {
            closeArchiveSidebar();
            showExpiredArchive();
        });
        LinearLayout.LayoutParams firstParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        firstParams.topMargin = Ui.dp(this, 28);
        panel.addView(expired, firstParams);

        LinearLayout deleted = archiveSidebarItem(
                "♲",
                "回收站",
                "可恢复或永久删除",
                false
        );
        LinearLayout.LayoutParams deletedParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        deletedParams.topMargin = Ui.dp(this, 12);
        panel.addView(deleted, deletedParams);
        deleted.setOnClickListener(view -> {
            closeArchiveSidebar();
            showDeletedArchive();
        });

        TextView hint = Ui.text(
                this,
                "每年重复的日期不会移入“已结束”，会自动计算下一年。",
                11,
                Ui.MUTED,
                false
        );
        hint.setLineSpacing(0, 1.25f);
        LinearLayout.LayoutParams hintParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = Ui.dp(this, 22);
        panel.addView(hint, hintParams);

        View spacer = new View(this);
        panel.addView(spacer, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        TextView settings = Ui.button(this, "⚙  设置", Ui.TEXT, Color.rgb(236, 234, 228), 15);
        settings.setContentDescription("打开设置与数据");
        settings.setOnClickListener(view -> {
            closeArchiveSidebar();
            archiveSidebar.postDelayed(this::showSettings, 220L);
        });
        panel.addView(settings, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 46)
        ));
        return panel;
    }

    private void showSettings() {
        new SettingsDialog(this).show();
    }

    private LinearLayout archiveSidebarItem(
            String iconText,
            String titleText,
            String descriptionText,
            boolean expired
    ) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 15), Ui.dp(this, 13), Ui.dp(this, 15));
        row.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 19, Ui.BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);

        TextView icon = Ui.text(this, iconText, 19, expired ? Ui.GREEN_TEXT : Ui.ACCENT, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(
                this,
                expired ? Ui.GREEN_SURFACE : Color.rgb(252, 239, 236),
                13
        ));
        row.addView(icon, Ui.linearParams(Ui.dp(this, 46), Ui.dp(this, 46)));

        LinearLayout text = Ui.vertical(this);
        TextView title = Ui.text(this, titleText, 15, Ui.TEXT, true);
        TextView description = Ui.text(this, descriptionText, 11, Ui.MUTED, false);
        text.addView(title, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams descriptionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        descriptionParams.topMargin = Ui.dp(this, 6);
        text.addView(description, descriptionParams);
        LinearLayout.LayoutParams textParams = Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = Ui.dp(this, 12);
        row.addView(text, textParams);

        TextView count = Ui.text(this, "0", 15, expired ? Ui.GREEN_TEXT : Ui.ACCENT, true);
        count.setGravity(Gravity.CENTER);
        count.setBackground(Ui.rounded(
                this,
                expired ? Ui.GREEN_SURFACE : Color.rgb(252, 239, 236),
                999
        ));
        count.setMinWidth(Ui.dp(this, 34));
        count.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 9), 0);
        row.addView(count, Ui.linearParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 34)));
        if (expired) {
            expiredArchiveCount = count;
        } else {
            deletedArchiveCount = count;
        }
        return row;
    }

    private void openArchiveSidebar() {
        if (archiveSidebarOpen || archiveSidebar == null) return;
        archiveSidebarOpen = true;
        renderArchiveCounts();
        archiveScrim.setVisibility(View.VISIBLE);
        archiveScrim.animate().alpha(1f).setDuration(180).start();
        archiveSidebar.setVisibility(View.VISIBLE);
        archiveSidebar.setTranslationX(archiveSidebar.getWidth() > 0
                ? archiveSidebar.getWidth()
                : Ui.dp(this, 310));
        archiveSidebar.animate().translationX(0f).setDuration(220).start();
    }

    private void closeArchiveSidebar() {
        if (!archiveSidebarOpen || archiveSidebar == null) return;
        archiveSidebarOpen = false;
        archiveScrim.animate().alpha(0f).setDuration(170).withEndAction(() ->
                archiveScrim.setVisibility(View.GONE)).start();
        archiveSidebar.animate()
                .translationX(archiveSidebar.getWidth())
                .setDuration(200)
                .withEndAction(() -> archiveSidebar.setVisibility(View.GONE))
                .start();
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        if (archiveSidebarOpen) {
            closeArchiveSidebar();
            return;
        }
        super.onBackPressed();
    }

    private void registerPredictiveBackIfSupported() {
        if (Build.VERSION.SDK_INT < 33) return;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (archiveSidebarOpen) {
                        closeArchiveSidebar();
                    } else {
                        finishAfterTransition();
                    }
                }
        );
    }

    private TextView createFilterButton(String filterId, String label) {
        TextView button = Ui.button(this, label, Ui.MUTED, Ui.BACKGROUND, 999);
        button.setTextSize(13);
        button.setPadding(Ui.dp(this, 14), 0, Ui.dp(this, 14), 0);
        button.setOnClickListener(view -> {
            currentFilter = filterId;
            selectFilter(filterId);
            renderEvents();
        });
        return button;
    }

    private void rebuildFilterButtons() {
        if (filterContainer == null || tagStore == null) return;
        List<TagStore.Tag> tags = tagStore.load();
        boolean currentExists = FILTER_ALL.equals(currentFilter);
        for (TagStore.Tag tag : tags) {
            currentExists = currentExists || tag.id.equals(currentFilter);
        }
        if (!currentExists) currentFilter = FILTER_ALL;

        filterContainer.removeAllViews();
        filterButtons.clear();
        addFilterButton(FILTER_ALL, "全部");
        for (TagStore.Tag tag : tags) {
            addFilterButton(tag.id, tag.name);
        }
        TextView manage = Ui.button(this, "＋ 标签", Ui.ACCENT, Color.rgb(252, 239, 236), 999);
        manage.setTextSize(13);
        manage.setContentDescription("管理日期标签");
        manage.setOnClickListener(view -> showTagManager());
        LinearLayout.LayoutParams manageParams = Ui.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 38)
        );
        manageParams.rightMargin = Ui.dp(this, 8);
        filterContainer.addView(manage, manageParams);
        selectFilter(currentFilter);
    }

    private void addFilterButton(String id, String label) {
        TextView button = createFilterButton(id, label);
        LinearLayout.LayoutParams params = Ui.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 38)
        );
        params.rightMargin = Ui.dp(this, 8);
        filterContainer.addView(button, params);
        filterButtons.put(id, button);
    }

    private void selectFilter(String selected) {
        for (Map.Entry<String, TextView> entry : filterButtons.entrySet()) {
            boolean active = entry.getKey().equals(selected);
            TextView button = entry.getValue();
            button.setTextColor(active ? Color.WHITE : Ui.MUTED);
            button.setBackground(Ui.ripple(this, active ? Ui.TEXT : Ui.BACKGROUND, 999));
        }
    }

    private void renderEvents() {
        activeList.removeAllViews();
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<EventWithOccurrence> active = new ArrayList<>();
        for (DateEvent event : store.load()) {
            try {
                EventWithOccurrence item = new EventWithOccurrence(event, DateCalculator.occurrence(event, today));
                if (item.occurrence.expired) {
                    continue;
                }
                if (FILTER_ALL.equals(currentFilter)
                        || currentFilter.equals(tagStore.resolve(event).id)) {
                    active.add(item);
                }
            } catch (RuntimeException ignored) {
                // An invalid imported event remains editable through future data repair builds.
            }
        }
        active.sort(Comparator.comparingLong(item -> item.occurrence.daysFromToday));

        for (EventWithOccurrence item : active) {
            activeList.addView(createEventRow(item, false), eventRowParams());
        }
        emptyView.setVisibility(active.isEmpty() ? View.VISIBLE : View.GONE);

        if (active.isEmpty()) {
            summaryCard.setVisibility(View.GONE);
        } else {
            summaryCard.setVisibility(View.VISIBLE);
            EventWithOccurrence first = active.get(0);
            summaryTitle.setText(first.event.title);
            String countText = EventCountdown.text(first.event, first.occurrence, now);
            summaryCount.setText(countText);
            summaryCount.setTextSize(EventCountdown.isHourMinute(countText) ? 18 : 24);
            summaryCard.setOnClickListener(view -> edit(first.event));
            summaryCard.setClickable(true);
        }
        renderArchiveCounts();
    }

    private void renderArchiveCounts() {
        if (expiredArchiveCount == null || deletedArchiveCount == null) return;
        int expired = 0;
        LocalDate today = LocalDate.now();
        for (DateEvent event : store.load()) {
            try {
                if (DateCalculator.occurrence(event, today).expired) expired++;
            } catch (RuntimeException ignored) {
                // Invalid dates remain editable but are not included in archive counts.
            }
        }
        expiredArchiveCount.setText(String.format(Locale.CHINA, "%d", expired));
        deletedArchiveCount.setText(String.format(Locale.CHINA, "%d", store.loadDeleted().size()));
    }

    private void showExpiredArchive() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        TextView title = Ui.text(this, "已结束", 20, Ui.TEXT, true);
        panel.addView(title, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        List<EventWithOccurrence> expired = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (DateEvent event : store.load()) {
            try {
                Occurrence occurrence = DateCalculator.occurrence(event, today);
                if (occurrence.expired) {
                    expired.add(new EventWithOccurrence(event, occurrence));
                }
            } catch (RuntimeException ignored) {
                // Invalid imported dates are omitted until the user repairs them.
            }
        }
        expired.sort((left, right) -> Long.compare(
                right.occurrence.daysFromToday,
                left.occurrence.daysFromToday
        ));
        TextView subtitle = Ui.text(
                this,
                expired.isEmpty()
                        ? "没有已结束的单次日期"
                        : "共 " + expired.size() + " 个 · 点击日期可修改或重新开启重复",
                12,
                Ui.MUTED,
                false
        );
        LinearLayout.LayoutParams subtitleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = Ui.dp(this, 7);
        panel.addView(subtitle, subtitleParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = Ui.vertical(this);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        if (expired.isEmpty()) {
            TextView empty = Ui.text(this, "已结束的单次日期会出现在这里", 13, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(Ui.rounded(this, Color.rgb(241, 239, 234), 18));
            list.addView(empty, Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 96)
            ));
        } else {
            for (EventWithOccurrence item : expired) {
                View row = createEventRow(item, true);
                row.setOnClickListener(view -> {
                    dialog.dismiss();
                    edit(item.event);
                });
                list.addView(row, eventRowParams());
            }
        }
        int listHeight = Math.min(
                Ui.dp(this, 360),
                Math.max(Ui.dp(this, 110), expired.size() * Ui.dp(this, 105))
        );
        LinearLayout.LayoutParams scrollParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                listHeight
        );
        scrollParams.topMargin = Ui.dp(this, 18);
        panel.addView(scroll, scrollParams);

        if (!expired.isEmpty()) {
            TextView clearExpired = Ui.button(
                    this,
                    "清空已结束",
                    Ui.ACCENT,
                    Color.rgb(252, 239, 236),
                    15
            );
            LinearLayout.LayoutParams clearParams = Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 46)
            );
            clearParams.topMargin = Ui.dp(this, 8);
            panel.addView(clearExpired, clearParams);
            clearExpired.setOnClickListener(view -> NianriConfirmDialog.show(
                    this,
                    "清空全部已结束日期？",
                    "共 " + expired.size() + " 个日期将移到回收站，之后仍可恢复。",
                    "移到回收站",
                    () -> {
                        List<Long> ids = new ArrayList<>();
                        for (EventWithOccurrence item : expired) ids.add(item.event.id);
                        int moved = store.moveAllToDeleted(ids);
                        ReminderScheduler.scheduleAll(this);
                        renderEvents();
                        renderArchiveCounts();
                        dialog.dismiss();
                        Toast.makeText(
                                this,
                                "已将 " + moved + " 个日期移到回收站",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            ));
        }

        TextView done = Ui.button(this, "完成", Color.WHITE, Ui.ACCENT, 15);
        done.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48)
        );
        doneParams.topMargin = Ui.dp(this, 8);
        panel.addView(done, doneParams);
        showStyledDialog(dialog, panel);
    }

    private void showDeletedArchive() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        TextView title = Ui.text(this, "回收站", 20, Ui.TEXT, true);
        panel.addView(title, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView subtitle = Ui.text(this, "", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams subtitleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = Ui.dp(this, 7);
        panel.addView(subtitle, subtitleParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = Ui.vertical(this);
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams scrollParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 360)
        );
        scrollParams.topMargin = Ui.dp(this, 18);
        panel.addView(scroll, scrollParams);

        TextView clearAll = Ui.button(
                this,
                "清空回收站",
                Ui.ACCENT,
                Color.rgb(252, 239, 236),
                15
        );
        clearAll.setOnClickListener(view -> {
            int count = store.loadDeleted().size();
            if (count == 0) return;
            NianriConfirmDialog.show(
                    this,
                    "永久清空回收站？",
                    "回收站中的 " + count + " 个日期将被永久删除，此操作无法恢复。",
                    "永久清空",
                    () -> {
                        int removed = store.clearDeleted();
                        renderArchiveCounts();
                        populateDeletedArchive(dialog, list, subtitle, clearAll);
                        Toast.makeText(
                                this,
                                "已永久删除 " + removed + " 个日期",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        });
        LinearLayout.LayoutParams clearParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 46)
        );
        clearParams.topMargin = Ui.dp(this, 8);
        panel.addView(clearAll, clearParams);

        TextView done = Ui.button(this, "完成", Color.WHITE, Ui.ACCENT, 15);
        done.setOnClickListener(view -> dialog.dismiss());
        LinearLayout.LayoutParams doneParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48)
        );
        doneParams.topMargin = Ui.dp(this, 8);
        panel.addView(done, doneParams);
        populateDeletedArchive(dialog, list, subtitle, clearAll);
        showStyledDialog(dialog, panel);
    }

    private void populateDeletedArchive(
            Dialog parent,
            LinearLayout list,
            TextView subtitle,
            TextView clearAll
    ) {
        List<DateEvent> deleted = store.loadDeleted();
        deleted.sort((left, right) -> Long.compare(right.deletedAt, left.deletedAt));
        clearAll.setVisibility(deleted.isEmpty() ? View.GONE : View.VISIBLE);
        subtitle.setText(deleted.isEmpty()
                ? "回收站暂时是空的"
                : "共 " + deleted.size() + " 个 · 恢复后会重新登记提醒");
        list.removeAllViews();
        if (deleted.isEmpty()) {
            TextView empty = Ui.text(this, "删除的日期会保留在这里", 13, Ui.MUTED, false);
            empty.setGravity(Gravity.CENTER);
            empty.setBackground(Ui.rounded(this, Color.rgb(241, 239, 234), 18));
            list.addView(empty, Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Ui.dp(this, 96)
            ));
            return;
        }
        for (DateEvent event : deleted) {
            LinearLayout row = deletedEventRow(parent, list, subtitle, clearAll, event);
            list.addView(row, eventRowParams());
        }
    }

    private LinearLayout deletedEventRow(
            Dialog parent,
            LinearLayout list,
            TextView subtitle,
            TextView clearAll,
            DateEvent event
    ) {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        card.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 18, Ui.BORDER, 1));

        LinearLayout top = Ui.horizontal(this);
        TextView icon = Ui.text(this, tagIcon(event), 18, Ui.TEXT, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(this, tagColor(event), 12));
        top.addView(icon, Ui.linearParams(Ui.dp(this, 42), Ui.dp(this, 42)));

        LinearLayout text = Ui.vertical(this);
        TextView name = Ui.text(this, event.title, 14, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView date = Ui.text(this, archivedDateText(event), 11, Ui.MUTED, false);
        date.setSingleLine(true);
        date.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dateParams.topMargin = Ui.dp(this, 6);
        text.addView(date, dateParams);
        TextView deletedTime = Ui.text(this, deletedTimeText(event), 11, Ui.MUTED, false);
        LinearLayout.LayoutParams deletedTimeParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        deletedTimeParams.topMargin = Ui.dp(this, 4);
        text.addView(deletedTime, deletedTimeParams);
        LinearLayout.LayoutParams textParams = Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = Ui.dp(this, 11);
        top.addView(text, textParams);
        card.addView(top, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = Ui.horizontal(this);
        TextView permanent = Ui.button(this, "永久删除", Ui.ACCENT, Color.rgb(252, 239, 236), 13);
        TextView restore = Ui.button(this, "恢复", Color.WHITE, Ui.ACCENT, 13);
        permanent.setOnClickListener(view -> confirmPermanentDelete(
                parent,
                list,
                subtitle,
                clearAll,
                event
        ));
        restore.setOnClickListener(view -> {
            if (store.restore(event.id)) {
                ReminderScheduler.scheduleAll(this);
                renderEvents();
                populateDeletedArchive(parent, list, subtitle, clearAll);
                Toast.makeText(this, "已恢复“" + event.title + "”", Toast.LENGTH_SHORT).show();
            }
        });
        actions.addView(permanent, Ui.weightedParams(Ui.dp(this, 42), 1));
        LinearLayout.LayoutParams restoreParams = Ui.weightedParams(Ui.dp(this, 42), 1);
        restoreParams.leftMargin = Ui.dp(this, 9);
        actions.addView(restore, restoreParams);
        LinearLayout.LayoutParams actionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = Ui.dp(this, 12);
        card.addView(actions, actionParams);
        return card;
    }

    private void confirmPermanentDelete(
            Dialog parent,
            LinearLayout list,
            TextView subtitle,
            TextView clearAll,
            DateEvent event
    ) {
        NianriConfirmDialog.show(
                this,
                "永久删除“" + event.title + "”？",
                "这个日期将从回收站中移除，删除后无法恢复。",
                "永久删除",
                () -> {
                    if (store.deletePermanently(event.id)) {
                        renderArchiveCounts();
                        populateDeletedArchive(parent, list, subtitle, clearAll);
                        Toast.makeText(this, "已永久删除", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private String deletedTimeText(DateEvent event) {
        if (event.deletedAt <= 0L) return "删除时间未知";
        return "删除于 " + java.time.Instant.ofEpochMilli(event.deletedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.CHINA));
    }

    private String archivedDateText(DateEvent event) {
        try {
            Occurrence occurrence = DateCalculator.occurrence(event, LocalDate.now());
            return occurrence.primaryDate + " · " + occurrence.secondaryDate;
        } catch (RuntimeException error) {
            String prefix = DateEvent.CALENDAR_LUNAR.equals(event.calendarType) ? "农历" : "公历";
            String year = event.yearKnown ? event.year + "年" : "";
            return prefix + " " + year + event.month + "月" + event.day + "日";
        }
    }

    private View createEventRow(EventWithOccurrence item, boolean expired) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 14), Ui.dp(this, 13));
        int fill = expired ? Color.rgb(241, 239, 234) : Ui.SURFACE;
        row.setBackground(Ui.roundedStroke(this, fill, 19, Ui.BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnClickListener(view -> edit(item.event));

        TextView icon = Ui.text(this, tagIcon(item.event), 21, Ui.TEXT, false);
        icon.setGravity(Gravity.CENTER);
        int iconFill = expired ? Color.rgb(229, 226, 220) : tagColor(item.event);
        icon.setBackground(Ui.rounded(this, iconFill, 14));
        row.addView(icon, Ui.linearParams(Ui.dp(this, 46), Ui.dp(this, 46)));

        LinearLayout middle = Ui.vertical(this);
        TextView title = Ui.text(this, item.event.title, 15, expired ? Ui.MUTED : Ui.TEXT, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        middle.addView(title, Ui.linearParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        String repeat = item.event.yearly ? "每年" : "不重复";
        TextView date = Ui.text(
                this,
                item.occurrence.primaryDate + " · " + item.occurrence.secondaryDate,
                12,
                Ui.MUTED,
                false
        );
        date.setSingleLine(true);
        date.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dateParams.topMargin = Ui.dp(this, 5);
        middle.addView(date, dateParams);
        TextView reminder = Ui.text(this, repeat + " · " + reminderText(item.event), 11, Ui.MUTED, false);
        LinearLayout.LayoutParams reminderParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        reminderParams.topMargin = Ui.dp(this, 3);
        middle.addView(reminder, reminderParams);
        LinearLayout.LayoutParams middleParams = Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        middleParams.setMargins(Ui.dp(this, 12), 0, Ui.dp(this, 10), 0);
        row.addView(middle, middleParams);

        String countdownText = EventCountdown.text(item.event, item.occurrence, LocalDateTime.now());
        TextView days = Ui.text(
                this,
                countdownText,
                expired ? 11 : EventCountdown.isHourMinute(countdownText) ? 12 : 14,
                expired ? Ui.MUTED : Ui.TEXT,
                !expired
        );
        days.setGravity(Gravity.CENTER | Gravity.END);
        days.setSingleLine(true);
        row.addView(days, Ui.linearParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 50)));
        return row;
    }

    private LinearLayout.LayoutParams eventRowParams() {
        LinearLayout.LayoutParams params = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = Ui.dp(this, 11);
        return params;
    }

    private void edit(DateEvent event) {
        Intent intent = new Intent(this, DateEditorActivity.class);
        intent.putExtra(DateEditorActivity.EXTRA_EVENT_ID, event.id);
        startActivity(intent);
    }

    public void showTagManager() {
        new TagManagerDialog(this, () -> {
            currentFilter = FILTER_ALL;
            rebuildFilterButtons();
            ReminderScheduler.scheduleAll(this);
            renderEvents();
        }).show();
    }

    public void requestCalendarImport() {
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            showCalendarImportPicker();
            return;
        }
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR}, REQUEST_CALENDAR);
    }

    private void showCalendarExportPicker() {
        LocalDate today = LocalDate.now();
        List<EventWithOccurrence> candidates = new ArrayList<>();
        for (DateEvent event : store.load()) {
            try {
                candidates.add(new EventWithOccurrence(event, DateCalculator.occurrence(event, today)));
            } catch (RuntimeException ignored) {
                // Invalid imported dates are skipped until the user repairs them.
            }
        }
        candidates.sort((left, right) -> {
            if (left.occurrence.expired != right.occurrence.expired) {
                return left.occurrence.expired ? 1 : -1;
            }
            return Long.compare(left.occurrence.daysFromToday, right.occurrence.daysFromToday);
        });
        if (candidates.isEmpty()) {
            Toast.makeText(this, "还没有可添加的日期", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean[] selected = new boolean[candidates.size()];
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();

        LinearLayout heading = Ui.horizontal(this);
        TextView title = Ui.text(this, "批量添加到日历", 20, Ui.TEXT, true);
        heading.addView(title, Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView selectAll = Ui.button(this, "全选", Ui.ACCENT, Color.rgb(252, 239, 236), 999);
        selectAll.setTextSize(12);
        heading.addView(selectAll, Ui.linearParams(Ui.dp(this, 62), Ui.dp(this, 36)));
        panel.addView(heading, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView selectionStatus = Ui.text(this, "选择要复制到本机日历的日期", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams statusParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = Ui.dp(this, 7);
        panel.addView(selectionStatus, statusParams);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setFillViewport(false);
        listScroll.setVerticalScrollBarEnabled(false);
        LinearLayout choices = Ui.vertical(this);
        listScroll.addView(choices, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout[] rows = new LinearLayout[candidates.size()];
        TextView[] markers = new TextView[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            markers[i] = selectionMarker(false);
            rows[i] = exportDateChoice(candidates.get(i), markers[i]);
            LinearLayout.LayoutParams rowParams = Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = Ui.dp(this, 9);
            choices.addView(rows[i], rowParams);
        }
        int listHeight = Math.min(
                Ui.dp(this, 390),
                Math.max(Ui.dp(this, 86), candidates.size() * Ui.dp(this, 85))
        );
        LinearLayout.LayoutParams scrollParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                listHeight
        );
        scrollParams.topMargin = Ui.dp(this, 18);
        panel.addView(listScroll, scrollParams);

        LinearLayout actions = Ui.horizontal(this);
        TextView cancel = Ui.button(this, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        TextView next = Ui.button(this, "下一步", Ui.MUTED, Color.rgb(239, 237, 231), 15);
        actions.addView(cancel, Ui.weightedParams(Ui.dp(this, 48), 1));
        LinearLayout.LayoutParams nextParams = Ui.weightedParams(Ui.dp(this, 48), 1.35f);
        nextParams.leftMargin = Ui.dp(this, 10);
        actions.addView(next, nextParams);
        LinearLayout.LayoutParams actionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = Ui.dp(this, 8);
        panel.addView(actions, actionParams);

        Runnable refreshSelection = () -> {
            int count = 0;
            for (int i = 0; i < selected.length; i++) {
                if (selected[i]) count++;
                styleSelectionRow(rows[i], markers[i], selected[i]);
            }
            boolean hasSelection = count > 0;
            selectionStatus.setText(hasSelection
                    ? "已选 " + count + " 个日期 · 将在下一步选择目标日历"
                    : "选择要复制到本机日历的日期");
            selectAll.setText(count == selected.length ? "清除" : "全选");
            next.setEnabled(hasSelection);
            next.setText(hasSelection ? "下一步  " + count : "下一步");
            next.setTextColor(hasSelection ? Color.WHITE : Ui.MUTED);
            next.setBackground(Ui.ripple(
                    this,
                    hasSelection ? Ui.ACCENT : Color.rgb(239, 237, 231),
                    15
            ));
        };
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            rows[i].setOnClickListener(view -> {
                selected[index] = !selected[index];
                refreshSelection.run();
            });
        }
        selectAll.setOnClickListener(view -> {
            boolean allSelected = true;
            for (boolean value : selected) {
                allSelected = allSelected && value;
            }
            for (int i = 0; i < selected.length; i++) {
                selected[i] = !allSelected;
            }
            refreshSelection.run();
        });
        cancel.setOnClickListener(view -> dialog.cancel());
        next.setOnClickListener(view -> {
            pendingCalendarExports.clear();
            for (int i = 0; i < candidates.size(); i++) {
                if (selected[i]) {
                    pendingCalendarExports.add(candidates.get(i).event.copy());
                }
            }
            if (!pendingCalendarExports.isEmpty()) {
                dialog.dismiss();
                requestCalendarExportPermission();
            }
        });
        refreshSelection.run();
        showStyledDialog(dialog, panel);
    }

    private LinearLayout dialogPanel() {
        LinearLayout panel = Ui.vertical(this);
        int side = Ui.dp(this, 20);
        panel.setPadding(side, Ui.dp(this, 21), side, Ui.dp(this, 20));
        panel.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 25, Ui.BORDER, 1));
        return panel;
    }

    private void showStyledDialog(Dialog dialog, View panel) {
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.46f;
        window.setAttributes(attributes);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenWidth - Ui.dp(this, 32), Ui.dp(this, 520));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private TextView selectionMarker(boolean selected) {
        TextView marker = Ui.text(this, selected ? "✓" : "", 14, Color.WHITE, true);
        marker.setGravity(Gravity.CENTER);
        marker.setContentDescription(selected ? "已选择" : "未选择");
        marker.setBackground(selected
                ? Ui.rounded(this, Ui.ACCENT, 8)
                : Ui.roundedStroke(this, Ui.SURFACE, 8, Ui.BORDER, 1));
        return marker;
    }

    private LinearLayout exportDateChoice(EventWithOccurrence item, TextView marker) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 13), Ui.dp(this, 12), Ui.dp(this, 13), Ui.dp(this, 12));
        row.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 17, Ui.BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(marker, Ui.linearParams(Ui.dp(this, 25), Ui.dp(this, 25)));

        TextView icon = Ui.text(this, tagIcon(item.event), 17, Ui.TEXT, false);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(this, tagColor(item.event), 11));
        LinearLayout.LayoutParams iconParams = Ui.linearParams(Ui.dp(this, 38), Ui.dp(this, 38));
        iconParams.leftMargin = Ui.dp(this, 11);
        row.addView(icon, iconParams);

        LinearLayout text = Ui.vertical(this);
        TextView name = Ui.text(this, item.event.title, 14, Ui.TEXT, true);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(name, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        String exactDate = DateCalculator.fullSolarText(item.occurrence.solarDate);
        String otherCalendar = DateEvent.CALENDAR_LUNAR.equals(item.event.calendarType)
                ? item.occurrence.primaryDate
                : item.occurrence.secondaryDate;
        TextView date = Ui.text(this, exactDate + " · " + otherCalendar, 11, Ui.MUTED, false);
        date.setSingleLine(true);
        date.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams dateParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dateParams.topMargin = Ui.dp(this, 5);
        text.addView(date, dateParams);
        LinearLayout.LayoutParams textParams = Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = Ui.dp(this, 11);
        row.addView(text, textParams);

        TextView repeat = Ui.text(
                this,
                item.occurrence.expired ? "已结束" : (item.event.yearly ? "每年" : "单次"),
                10,
                item.occurrence.expired ? Ui.MUTED : Ui.GREEN_TEXT,
                true
        );
        repeat.setGravity(Gravity.CENTER);
        repeat.setBackground(Ui.rounded(
                this,
                item.occurrence.expired ? Color.rgb(239, 237, 231) : Ui.GREEN_SURFACE,
                999
        ));
        LinearLayout.LayoutParams repeatParams = Ui.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(this, 26)
        );
        repeatParams.leftMargin = Ui.dp(this, 8);
        row.addView(repeat, repeatParams);
        repeat.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 9), 0);
        return row;
    }

    private void styleSelectionRow(LinearLayout row, TextView marker, boolean selected) {
        row.setBackground(Ui.roundedStroke(
                this,
                selected ? Color.rgb(254, 244, 242) : Ui.SURFACE,
                17,
                selected ? Ui.ACCENT : Ui.BORDER,
                1
        ));
        marker.setText(selected ? "✓" : "");
        marker.setTextColor(Color.WHITE);
        marker.setContentDescription(selected ? "已选择" : "未选择");
        marker.setBackground(selected
                ? Ui.rounded(this, Ui.ACCENT, 8)
                : Ui.roundedStroke(this, Ui.SURFACE, 8, Ui.BORDER, 1));
    }

    private LinearLayout calendarChoice(CalendarExporter.CalendarTarget calendar) {
        boolean local = calendar.accountName.isEmpty()
                || calendar.accountName.equalsIgnoreCase(calendar.displayName);
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 14), Ui.dp(this, 13), Ui.dp(this, 13), Ui.dp(this, 13));
        row.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 18, Ui.BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);

        TextView badge = Ui.text(this, local ? "本机" : "账户", 11, local ? Ui.GREEN_TEXT : Ui.ACCENT, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(Ui.rounded(
                this,
                local ? Ui.GREEN_SURFACE : Color.rgb(252, 239, 236),
                12
        ));
        row.addView(badge, Ui.linearParams(Ui.dp(this, 46), Ui.dp(this, 46)));

        LinearLayout text = Ui.vertical(this);
        TextView name = Ui.text(this, calendarDisplayName(calendar), 15, Ui.TEXT, true);
        text.addView(name, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        String detail = local
                ? "仅保存在当前手机"
                : calendar.accountName + " · 可能随账户同步";
        TextView description = Ui.text(this, detail, 11, Ui.MUTED, false);
        LinearLayout.LayoutParams detailParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        detailParams.topMargin = Ui.dp(this, 6);
        text.addView(description, detailParams);
        LinearLayout.LayoutParams textParams = Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = Ui.dp(this, 13);
        row.addView(text, textParams);

        TextView arrow = Ui.text(this, "›", 27, Ui.ACCENT, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, Ui.linearParams(Ui.dp(this, 24), Ui.dp(this, 46)));
        return row;
    }

    private String calendarDisplayName(CalendarExporter.CalendarTarget calendar) {
        if (calendar.displayName.equalsIgnoreCase("Phone")) {
            return "手机日历";
        }
        return calendar.displayName;
    }

    private void requestCalendarExportPermission() {
        boolean canRead = checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        boolean canWrite = checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
        if (canRead && canWrite) {
            showWritableCalendarPicker();
            return;
        }
        requestPermissions(
                new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR},
                REQUEST_CALENDAR_EXPORT
        );
    }

    private void showWritableCalendarPicker() {
        List<CalendarExporter.CalendarTarget> calendars;
        try {
            calendars = CalendarExporter.listWritableCalendars(this);
        } catch (RuntimeException error) {
            pendingCalendarExports.clear();
            Toast.makeText(this, "无法读取可写的本机日历", Toast.LENGTH_LONG).show();
            return;
        }
        if (calendars.isEmpty()) {
            pendingCalendarExports.clear();
            Toast.makeText(this, "未找到可写日历，请先在系统日历中添加账户", Toast.LENGTH_LONG).show();
            return;
        }
        if (calendars.size() == 1) {
            exportSelectedDates(calendars.get(0));
            return;
        }
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        TextView title = Ui.text(this, "选择目标日历", 20, Ui.TEXT, true);
        panel.addView(title, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView subtitle = Ui.text(
                this,
                "已选 " + pendingCalendarExports.size() + " 个日期 · 点击下方日历即可添加",
                12,
                Ui.MUTED,
                false
        );
        LinearLayout.LayoutParams subtitleParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        subtitleParams.topMargin = Ui.dp(this, 7);
        panel.addView(subtitle, subtitleParams);

        ScrollView listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        LinearLayout calendarList = Ui.vertical(this);
        listScroll.addView(calendarList, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        for (CalendarExporter.CalendarTarget calendar : calendars) {
            LinearLayout row = calendarChoice(calendar);
            row.setOnClickListener(view -> {
                dialog.dismiss();
                exportSelectedDates(calendar);
            });
            LinearLayout.LayoutParams rowParams = Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = Ui.dp(this, 10);
            calendarList.addView(row, rowParams);
        }
        int listHeight = Math.min(
                Ui.dp(this, 320),
                Math.max(Ui.dp(this, 82), calendars.size() * Ui.dp(this, 82))
        );
        LinearLayout.LayoutParams listParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                listHeight
        );
        listParams.topMargin = Ui.dp(this, 19);
        panel.addView(listScroll, listParams);

        TextView cancel = Ui.button(this, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        cancel.setOnClickListener(view -> dialog.cancel());
        LinearLayout.LayoutParams cancelParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48)
        );
        cancelParams.topMargin = Ui.dp(this, 8);
        panel.addView(cancel, cancelParams);
        dialog.setOnCancelListener(ignored -> pendingCalendarExports.clear());
        showStyledDialog(dialog, panel);
    }

    private void exportSelectedDates(CalendarExporter.CalendarTarget target) {
        List<DateEvent> selected = new ArrayList<>(pendingCalendarExports);
        pendingCalendarExports.clear();
        Toast.makeText(this, "正在添加到“" + calendarDisplayName(target) + "”…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            CalendarExporter.ExportResult result = CalendarExporter.export(this, target, selected);
            runOnUiThread(() -> Toast.makeText(
                    this,
                    calendarExportMessage(result),
                    Toast.LENGTH_LONG
            ).show());
        }, "nianri-calendar-export").start();
    }

    private String calendarExportMessage(CalendarExporter.ExportResult result) {
        StringBuilder message = new StringBuilder();
        if (result.added > 0) {
            message.append("已添加 ").append(result.added).append(" 个日期");
        } else {
            message.append("没有新增日期");
        }
        if (result.duplicates > 0) {
            message.append("，已跳过 ").append(result.duplicates).append(" 个重复事件");
        }
        if (result.failed > 0) {
            message.append("，").append(result.failed).append(" 个添加失败");
        }
        if (result.recurringLunar > 0) {
            message.append("\n农历重复日期已写入下一次公历日期，年度提醒仍由念日管理。");
        }
        return message.toString();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CALENDAR) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showCalendarImportPicker();
            } else {
                Toast.makeText(this, "未获得日历读取权限；离线功能不受影响", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_CALENDAR_EXPORT) {
            boolean granted = checkSelfPermission(Manifest.permission.READ_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                    == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                showWritableCalendarPicker();
            } else {
                pendingCalendarExports.clear();
                Toast.makeText(this, "需要日历读写权限才能批量添加", Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_NOTIFICATIONS) {
            ReminderScheduler.scheduleAll(this);
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getWindow().getDecorView().post(this::maybeRequestExactAlarmPermissionIfNeeded);
            } else {
                Toast.makeText(this, "未开启通知权限，念日无法显示提醒", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showCalendarImportPicker() {
        List<DateEvent> candidates;
        try {
            candidates = CalendarImporter.readUpcoming(this, 366);
        } catch (SecurityException error) {
            Toast.makeText(this, "无法读取本机日历", Toast.LENGTH_SHORT).show();
            return;
        }
        if (candidates.isEmpty()) {
            Toast.makeText(this, "未来一年没有可导入的本机日历事件", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[candidates.size()];
        boolean[] selected = new boolean[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            DateEvent event = candidates.get(i);
            labels[i] = event.title + "\n" + event.year + "年" + event.month + "月" + event.day + "日"
                    + (event.yearly ? " · 每年" : "");
        }
        showCalendarImportChoices(candidates, labels, selected);
    }

    private void showCalendarImportChoices(
            List<DateEvent> candidates,
            String[] labels,
            boolean[] selected
    ) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = dialogPanel();
        TextView title = Ui.text(this, "从本机日历导入", 20, Ui.TEXT, true);
        panel.addView(title, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        TextView status = Ui.text(this, "选择要导入的日期", 12, Ui.MUTED, false);
        LinearLayout.LayoutParams statusParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.topMargin = Ui.dp(this, 7);
        panel.addView(status, statusParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout choices = Ui.vertical(this);
        LinearLayout[] rows = new LinearLayout[candidates.size()];
        TextView[] markers = new TextView[candidates.size()];
        for (int i = 0; i < candidates.size(); i++) {
            markers[i] = selectionMarker(false);
            rows[i] = importDateChoice(candidates.get(i), labels[i], markers[i]);
            LinearLayout.LayoutParams rowParams = Ui.linearParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.bottomMargin = Ui.dp(this, 8);
            choices.addView(rows[i], rowParams);
        }
        scroll.addView(choices, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int height = Math.min(Ui.dp(this, 390), Math.max(Ui.dp(this, 90), candidates.size() * Ui.dp(this, 76)));
        LinearLayout.LayoutParams scrollParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                height
        );
        scrollParams.topMargin = Ui.dp(this, 16);
        panel.addView(scroll, scrollParams);

        LinearLayout actions = Ui.horizontal(this);
        TextView cancel = Ui.button(this, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        TextView importButton = Ui.button(this, "导入", Ui.MUTED, Color.rgb(239, 237, 231), 15);
        actions.addView(cancel, Ui.weightedParams(Ui.dp(this, 48), 1));
        LinearLayout.LayoutParams importParams = Ui.weightedParams(Ui.dp(this, 48), 1.25f);
        importParams.leftMargin = Ui.dp(this, 9);
        actions.addView(importButton, importParams);
        LinearLayout.LayoutParams actionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.topMargin = Ui.dp(this, 8);
        panel.addView(actions, actionParams);

        Runnable refresh = () -> {
            int count = 0;
            for (int i = 0; i < selected.length; i++) {
                if (selected[i]) count++;
                styleSelectionRow(rows[i], markers[i], selected[i]);
            }
            boolean any = count > 0;
            status.setText(any ? "已选 " + count + " 个日期" : "选择要导入的日期");
            importButton.setEnabled(any);
            importButton.setText(any ? "导入  " + count : "导入");
            importButton.setTextColor(any ? Color.WHITE : Ui.MUTED);
            importButton.setBackground(Ui.ripple(
                    this,
                    any ? Ui.ACCENT : Color.rgb(239, 237, 231),
                    15
            ));
        };
        for (int i = 0; i < rows.length; i++) {
            final int index = i;
            rows[i].setOnClickListener(view -> {
                selected[index] = !selected[index];
                refresh.run();
            });
        }
        cancel.setOnClickListener(view -> dialog.dismiss());
        importButton.setOnClickListener(view -> {
            boolean any = false;
            for (boolean value : selected) any = any || value;
            if (!any) return;
            dialog.dismiss();
            importSelected(candidates, selected);
        });
        refresh.run();
        showStyledDialog(dialog, panel);
    }

    private LinearLayout importDateChoice(DateEvent event, String label, TextView marker) {
        LinearLayout row = Ui.horizontal(this);
        row.setPadding(Ui.dp(this, 13), Ui.dp(this, 11), Ui.dp(this, 13), Ui.dp(this, 11));
        row.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 17, Ui.BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);
        row.addView(marker, Ui.linearParams(Ui.dp(this, 25), Ui.dp(this, 25)));

        LinearLayout text = Ui.vertical(this);
        TextView name = Ui.text(this, event.title, 14, Ui.TEXT, true);
        TextView date = Ui.text(this, label.substring(label.indexOf('\n') + 1), 11, Ui.MUTED, false);
        text.addView(name, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        LinearLayout.LayoutParams dateParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        dateParams.topMargin = Ui.dp(this, 5);
        text.addView(date, dateParams);
        LinearLayout.LayoutParams textParams = Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        textParams.leftMargin = Ui.dp(this, 11);
        row.addView(text, textParams);
        return row;
    }

    private void importSelected(List<DateEvent> candidates, boolean[] selected) {
        List<DateEvent> events = store.load();
        int imported = 0;
        for (int i = 0; i < candidates.size(); i++) {
            if (!selected[i]) continue;
            DateEvent candidate = candidates.get(i);
            TagStore.Tag resolvedTag = tagStore.resolve(candidate);
            candidate.tagId = resolvedTag.id;
            candidate.type = resolvedTag.name;
            boolean duplicate = false;
            for (DateEvent existing : events) {
                if (!candidate.externalId.isEmpty()
                        && candidate.externalId.equals(existing.externalId)
                        && candidate.title.equals(existing.title)) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                events.add(candidate);
                imported++;
            }
        }
        store.save(events);
        ReminderScheduler.scheduleAll(this);
        renderEvents();
        Toast.makeText(this, imported == 0 ? "没有新增事件" : "已导入 " + imported + " 个事件", Toast.LENGTH_LONG).show();
    }

    private boolean requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            return true;
        }
        return false;
    }

    private void maybeRequestExactAlarmPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < 31 || ReminderScheduler.canScheduleExactAlarms(this)) {
            return;
        }
        android.content.SharedPreferences preferences = getSharedPreferences("nianri_settings", MODE_PRIVATE);
        if (preferences.getBoolean("exact_alarm_prompted", false)) {
            return;
        }
        preferences.edit().putBoolean("exact_alarm_prompted", true).apply();
        NianriConfirmDialog.show(
                this,
                "开启准点提醒？",
                "念日需要系统“闹钟和提醒”权限，才能在设定的整点尽可能准时通知。未开启时，Android 可能延迟提醒。",
                "去开启",
                this::requestExactAlarmAccess
        );
    }

    public void requestExactAlarmAccess() {
        if (Build.VERSION.SDK_INT < 31) {
            Toast.makeText(this, "当前系统已支持准点提醒", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            openApplicationSettings();
        }
    }

    public void openNotificationSettings() {
        ReminderScheduler.ensureNotificationChannel(this);
        try {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                    .putExtra(
                            Settings.EXTRA_CHANNEL_ID,
                            ReminderScheduler.notificationChannelId(this)
                    );
            startActivity(intent);
        } catch (ActivityNotFoundException error) {
            openApplicationSettings();
        }
    }

    public void openBackgroundSettings() {
        BackgroundAccessGuide guide = BackgroundAccessGuide.forDevice(
                Build.MANUFACTURER,
                Build.BRAND
        );
        BackgroundAccessGuideDialog.show(
                this,
                guide,
                () -> launchBackgroundSettingsPage(guide),
                this::openApplicationSettings,
                this::openBatteryOptimizationSettings
        );
    }

    private void launchBackgroundSettingsPage(BackgroundAccessGuide guide) {
        for (BackgroundAccessGuide.Target target : guide.targets) {
            try {
                startActivity(new Intent().setComponent(new ComponentName(
                        target.packageName,
                        target.className
                )));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
                // Try the next page used by another release of the same vendor system.
            }
        }
        if (guide.hasDedicatedSettingsPage()) {
            Toast.makeText(
                    this,
                    "这台手机的专用入口不可用，已打开念日的应用信息",
                    Toast.LENGTH_LONG
            ).show();
        }
        openApplicationSettings();
    }

    public void openBatteryOptimizationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        } catch (ActivityNotFoundException | SecurityException error) {
            openApplicationSettings();
        }
    }

    private void openApplicationSettings() {
        Intent intent = new Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }

    private void maybeAutoCheckAuthoritySources() {
        android.content.SharedPreferences preferences = getSharedPreferences("nianri_settings", MODE_PRIVATE);
        if (!preferences.getBoolean("auto_authority_check", false)) {
            return;
        }
        String raw = preferences.getString("last_authority_check", "");
        boolean due = true;
        if (raw != null && !raw.isEmpty()) {
            try {
                LocalDateTime last = LocalDateTime.parse(raw, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                due = ChronoUnit.DAYS.between(last, LocalDateTime.now()) >= 30;
            } catch (RuntimeException ignored) {
                due = true;
            }
        }
        if (due) {
            AuthorityDataChecker.check(this, (standard, observatory, checkedAt) -> { });
        }
    }

    private String tagIcon(DateEvent event) {
        TagStore.Tag tag = tagStore.resolve(event);
        if (TagStore.TAG_BIRTHDAY.equals(tag.style)) return "🎂";
        if (TagStore.TAG_ANNIVERSARY.equals(tag.style)) return "💞";
        if (TagStore.TAG_OTHER.equals(tag.style)) return "📌";
        return "🏷";
    }

    private int tagColor(DateEvent event) {
        TagStore.Tag tag = tagStore.resolve(event);
        if (TagStore.TAG_BIRTHDAY.equals(tag.style)) return Color.rgb(252, 233, 229);
        if (TagStore.TAG_ANNIVERSARY.equals(tag.style)) return Color.rgb(238, 232, 250);
        if (TagStore.TAG_OTHER.equals(tag.style)) return Color.rgb(231, 238, 252);
        int[] palette = {
                Color.rgb(232, 242, 238),
                Color.rgb(249, 239, 222),
                Color.rgb(231, 238, 252),
                Color.rgb(242, 233, 247)
        };
        return palette[Math.floorMod(tag.id.hashCode(), palette.length)];
    }

    private static String reminderText(DateEvent event) {
        if (!event.reminderEnabled) {
            return "未开启提醒";
        }
        List<String> values = new ArrayList<>();
        if (event.reminderDays.contains(7)) values.add("提前7天");
        if (event.reminderDays.contains(1)) values.add("提前1天");
        if (event.reminderDays.contains(0)) values.add("当天");
        return String.join("、", values)
                + " · "
                + ReminderTime.format(event.reminderHour)
                + "提醒";
    }

    private static final class EventWithOccurrence {
        final DateEvent event;
        final Occurrence occurrence;

        EventWithOccurrence(DateEvent event, Occurrence occurrence) {
            this.event = event;
            this.occurrence = occurrence;
        }
    }
}

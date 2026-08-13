package com.nianri.app;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class SettingsDialog {
    private final MainActivity activity;
    private final SharedPreferences preferences;
    private Dialog dialog;
    private LinearLayout panel;
    private boolean childPage;

    public SettingsDialog(MainActivity activity) {
        this.activity = activity;
        this.preferences = activity.getSharedPreferences("nianri_settings", Context.MODE_PRIVATE);
    }

    public void show() {
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        panel = Ui.vertical(activity);
        int side = Ui.dp(activity, 20);
        panel.setPadding(side, Ui.dp(activity, 20), side, Ui.dp(activity, 18));
        panel.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 25, Ui.BORDER, 1));
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnKeyListener((source, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    && event.getAction() == KeyEvent.ACTION_UP
                    && childPage) {
                renderHome();
                return true;
            }
            return false;
        });
        renderHome();
        dialog.show();
        styleDialog();
    }

    private void renderHome() {
        LinearLayout body = startPage("设置", false);

        LinearLayout statusCard = Ui.vertical(activity);
        statusCard.setPadding(
                Ui.dp(activity, 16),
                Ui.dp(activity, 14),
                Ui.dp(activity, 16),
                Ui.dp(activity, 14)
        );
        statusCard.setBackground(Ui.roundedStroke(
                activity,
                Ui.GREEN_SURFACE,
                18,
                Color.rgb(212, 229, 217),
                1
        ));
        statusCard.addView(Ui.text(activity, "提醒状态", 12, Color.rgb(86, 129, 100), true));
        TextView status = Ui.text(activity, reminderSummaryText(), 15, Ui.GREEN_TEXT, true);
        statusCard.addView(status, matchWrap(7));
        body.addView(statusCard, matchWrap(2));

        LinearLayout reminder = categoryRow(
                "铃",
                "提醒设置",
                "通知、声音、振动、准点权限与测试",
                false
        );
        reminder.setOnClickListener(view -> renderReminderSettings());
        body.addView(reminder, matchWrap(13));

        LinearLayout dates = categoryRow(
                "签",
                "日期与标签",
                "标签管理与本机日历导入",
                false
        );
        dates.setOnClickListener(view -> renderDateSettings());
        body.addView(dates, matchWrap(9));

        LinearLayout data = categoryRow(
                "农",
                "农历与数据",
                "离线范围、来源校验与自动检查",
                true
        );
        data.setOnClickListener(view -> renderDataSettings());
        body.addView(data, matchWrap(9));

        LinearLayout about = categoryRow(
                "i",
                "关于念日",
                "版本、数据存储与权限说明",
                true
        );
        about.setOnClickListener(view -> renderAbout());
        body.addView(about, matchWrap(9));

        TextView version = Ui.text(activity, versionText(), 11, Ui.MUTED, false);
        version.setGravity(Gravity.CENTER);
        body.addView(version, matchWrap(18));
    }

    private void renderReminderSettings() {
        LinearLayout body = startPage("提醒设置", true);
        addSectionTitle(body, "当前状态");
        TextView reminderStatus = infoText(reminderStatusText());
        body.addView(reminderStatus, matchWrap(0));

        Switch soundEnabled = settingSwitch(
                "声音提醒",
                ReminderScheduler.isReminderSoundEnabled(activity)
        );
        body.addView(soundEnabled, matchWrap(10));
        Switch vibrationEnabled = settingSwitch(
                "振动提醒",
                ReminderScheduler.isReminderVibrationEnabled(activity)
        );
        body.addView(vibrationEnabled, matchWrap(1));
        TextView alertHint = Ui.text(
                activity,
                "每个日期默认跟随这里的设置，也可在日期编辑页单独指定声音与振动。静音、勿扰及频道优先级仍由系统控制。",
                12,
                Ui.MUTED,
                false
        );
        alertHint.setLineSpacing(0, 1.18f);
        body.addView(alertHint, matchWrap(5));

        addSectionTitle(body, "系统与测试");
        TextView notificationSettings = actionButton(
                "系统通知、声音与振动设置",
                Ui.TEXT,
                Ui.SURFACE
        );
        body.addView(notificationSettings, matchHeight(48, 0));

        TextView exactAlarmSettings = null;
        if (Build.VERSION.SDK_INT >= 31) {
            boolean exact = ReminderScheduler.canScheduleExactAlarms(activity);
            exactAlarmSettings = actionButton(
                    exact ? "准点提醒已开启" : "开启准点提醒",
                    exact ? Ui.GREEN_TEXT : Ui.ACCENT,
                    exact ? Ui.GREEN_SURFACE : Color.rgb(252, 239, 236)
            );
            body.addView(exactAlarmSettings, matchHeight(48, 8));
        }

        TextView immediateTest = actionButton(
                "立即测试通知",
                Ui.ACCENT,
                Color.rgb(252, 239, 236)
        );
        body.addView(immediateTest, matchHeight(48, 8));
        TextView timedTest = actionButton("1 分钟后测试提醒", Color.WHITE, Ui.ACCENT);
        body.addView(timedTest, matchHeight(48, 8));
        TextView testStatus = infoText(ReminderScheduler.testStatusText(activity));
        body.addView(testStatus, matchWrap(8));

        addSectionTitle(body, "后台运行");
        TextView backgroundHint = Ui.text(
                activity,
                backgroundReminderHint(),
                12,
                Ui.MUTED,
                false
        );
        backgroundHint.setLineSpacing(0, 1.18f);
        body.addView(backgroundHint, matchWrap(0));
        TextView backgroundSettings = actionButton("设置自启动与后台运行", Ui.TEXT, Ui.SURFACE);
        body.addView(backgroundSettings, matchHeight(48, 9));
        TextView batterySettings = actionButton("电池优化设置", Ui.TEXT, Ui.SURFACE);
        body.addView(batterySettings, matchHeight(48, 8));

        soundEnabled.setOnCheckedChangeListener((button, checked) -> {
            ReminderScheduler.setReminderSoundEnabled(activity, checked);
            reminderStatus.setText(reminderStatusText());
        });
        vibrationEnabled.setOnCheckedChangeListener((button, checked) -> {
            ReminderScheduler.setReminderVibrationEnabled(activity, checked);
            reminderStatus.setText(reminderStatusText());
        });
        notificationSettings.setOnClickListener(view -> {
            dialog.dismiss();
            activity.openNotificationSettings();
        });
        if (exactAlarmSettings != null) {
            exactAlarmSettings.setOnClickListener(view -> {
                dialog.dismiss();
                activity.requestExactAlarmAccess();
            });
        }
        immediateTest.setOnClickListener(view -> {
            if (!ReminderScheduler.sendImmediateTestNotification(activity)) {
                Toast.makeText(activity, "通知未开启，请先允许念日显示通知", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                activity.openNotificationSettings();
                return;
            }
            testStatus.setText(ReminderScheduler.testStatusText(activity));
            Toast.makeText(activity, "已提交测试通知，请查看顶部或通知栏", Toast.LENGTH_LONG).show();
        });
        timedTest.setOnClickListener(view -> {
            if (!ReminderScheduler.canScheduleExactAlarms(activity)) {
                Toast.makeText(activity, "请先开启“闹钟和提醒”准点权限", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                activity.requestExactAlarmAccess();
                return;
            }
            if (!ReminderScheduler.scheduleTestReminder(activity)) {
                Toast.makeText(activity, "通知未开启，请先允许念日显示通知", Toast.LENGTH_LONG).show();
                dialog.dismiss();
                activity.openNotificationSettings();
                return;
            }
            dialog.dismiss();
            Toast.makeText(activity, "已安排测试，可划掉应用并等待 1 分钟", Toast.LENGTH_LONG).show();
        });
        backgroundSettings.setOnClickListener(view -> {
            dialog.dismiss();
            activity.openBackgroundSettings();
        });
        batterySettings.setOnClickListener(view -> {
            dialog.dismiss();
            activity.openBatteryOptimizationSettings();
        });
    }

    private void renderDateSettings() {
        LinearLayout body = startPage("日期与标签", true);
        addSectionTitle(body, "日期标签");
        addInfoCard(
                body,
                "生日、纪念日与其它",
                "默认标签可改名，也可以新建自己的标签；删除标签不会删除日期。"
        );
        TextView manageTags = actionButton("管理日期标签", Ui.TEXT, Ui.SURFACE);
        body.addView(manageTags, matchHeight(48, 9));

        addSectionTitle(body, "本机日历");
        TextView calendarInfo = infoText(
                "导入时只读取本机日历；首页“批量”功能用于把多个日期写入指定日历。所有事件只在手机本地处理。"
        );
        body.addView(calendarInfo, matchWrap(0));
        TextView importButton = actionButton("从本机日历导入", Ui.TEXT, Ui.SURFACE);
        body.addView(importButton, matchHeight(48, 9));

        manageTags.setOnClickListener(view -> {
            dialog.dismiss();
            activity.showTagManager();
        });
        importButton.setOnClickListener(view -> {
            dialog.dismiss();
            activity.requestCalendarImport();
        });
    }

    private void renderDataSettings() {
        LinearLayout body = startPage("农历与数据", true);
        addSectionTitle(body, "离线农历");
        addInfoCard(
                body,
                "1900—2100年",
                "无网络也可换算、排序和提醒。编排规则依据现行 GB/T 33661-2017。"
        );

        addSectionTitle(body, "权威来源校验");
        TextView status = infoText(statusText());
        body.addView(status, matchWrap(0));
        TextView check = actionButton(
                "联网检查权威来源",
                Ui.ACCENT,
                Color.rgb(252, 239, 236)
        );
        body.addView(check, matchHeight(48, 9));
        Switch autoCheck = settingSwitch(
                "每月自动检查一次",
                preferences.getBoolean("auto_authority_check", false)
        );
        body.addView(autoCheck, matchWrap(8));
        TextView standardLink = sourceLink(
                "国家标准全文公开系统",
                AuthorityDataChecker.STANDARD_URL
        );
        TextView observatoryLink = sourceLink(
                "中国科学院紫金山天文台",
                AuthorityDataChecker.OBSERVATORY_URL
        );
        body.addView(standardLink, matchHeight(44, 8));
        body.addView(observatoryLink, matchHeight(44, 6));

        autoCheck.setOnCheckedChangeListener((button, checked) -> preferences.edit()
                .putBoolean("auto_authority_check", checked)
                .apply());
        check.setOnClickListener(view -> {
            check.setEnabled(false);
            check.setText("正在检查…");
            AuthorityDataChecker.check(activity, (standard, observatory, checkedAt) -> {
                check.setEnabled(true);
                check.setText("联网检查权威来源");
                status.setText(statusText());
                Toast.makeText(
                        activity,
                        standard && observatory ? "两个权威来源均可访问" : "部分来源暂时无法访问，不影响离线功能",
                        Toast.LENGTH_LONG
                ).show();
            });
        });
    }

    private void renderAbout() {
        LinearLayout body = startPage("关于念日", true);
        addSectionTitle(body, "版本");
        addInfoCard(
                body,
                versionText(),
                "一个轻量的中国农历与公历重要日期提醒工具。"
        );

        addSectionTitle(body, "数据与隐私");
        TextView privacy = infoText(
                "日期、标签和回收站数据均保存在本机，不需要账号，也不会上传到服务器。联网功能目前只检查官方来源是否可访问。"
        );
        body.addView(privacy, matchWrap(0));

        addSectionTitle(body, "权限用途");
        TextView permissions = infoText(
                "通知与准点权限用于按时提醒；日历权限只在导入或批量写入时使用；网络权限只用于权威来源检查。"
        );
        body.addView(permissions, matchWrap(0));
    }

    private LinearLayout startPage(String titleText, boolean child) {
        childPage = child;
        panel.removeAllViews();
        LinearLayout header = Ui.horizontal(activity);
        if (child) {
            ImageButton back = new ImageButton(activity);
            back.setImageResource(R.drawable.ic_arrow_back);
            back.setBackground(Ui.ripple(activity, Color.rgb(239, 237, 231), 999));
            back.setPadding(
                    Ui.dp(activity, 8),
                    Ui.dp(activity, 8),
                    Ui.dp(activity, 8),
                    Ui.dp(activity, 8)
            );
            back.setContentDescription("返回设置分类");
            back.setOnClickListener(view -> renderHome());
            LinearLayout.LayoutParams backParams = Ui.linearParams(
                    Ui.dp(activity, 38),
                    Ui.dp(activity, 38)
            );
            backParams.rightMargin = Ui.dp(activity, 10);
            header.addView(back, backParams);
        }
        TextView title = Ui.text(activity, titleText, 21, Ui.TEXT, true);
        header.addView(title, Ui.weightedParams(ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView done = Ui.button(
                activity,
                "完成",
                Ui.ACCENT,
                Color.rgb(252, 239, 236),
                999
        );
        done.setTextSize(12);
        done.setOnClickListener(view -> dialog.dismiss());
        header.addView(done, Ui.linearParams(Ui.dp(activity, 62), Ui.dp(activity, 36)));
        panel.addView(header, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout body = Ui.vertical(activity);
        body.setPadding(0, Ui.dp(activity, 15), 0, Ui.dp(activity, 6));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        int screenHeight = activity.getResources().getDisplayMetrics().heightPixels;
        int available = Math.max(Ui.dp(activity, 300), screenHeight - Ui.dp(activity, 190));
        int scrollHeight = Math.min(Ui.dp(activity, 540), available);
        LinearLayout.LayoutParams scrollParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                scrollHeight
        );
        scrollParams.topMargin = Ui.dp(activity, 5);
        panel.addView(scroll, scrollParams);
        return body;
    }

    private LinearLayout categoryRow(
            String iconText,
            String titleText,
            String descriptionText,
            boolean green
    ) {
        LinearLayout row = Ui.horizontal(activity);
        row.setPadding(
                Ui.dp(activity, 14),
                Ui.dp(activity, 13),
                Ui.dp(activity, 13),
                Ui.dp(activity, 13)
        );
        row.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 18, Ui.BORDER, 1));
        row.setClickable(true);
        row.setFocusable(true);

        TextView icon = Ui.text(
                activity,
                iconText,
                iconText.length() == 1 ? 15 : 11,
                green ? Ui.GREEN_TEXT : Ui.ACCENT,
                true
        );
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(
                activity,
                green ? Ui.GREEN_SURFACE : Color.rgb(252, 239, 236),
                13
        ));
        row.addView(icon, Ui.linearParams(Ui.dp(activity, 44), Ui.dp(activity, 44)));

        LinearLayout text = Ui.vertical(activity);
        TextView title = Ui.text(activity, titleText, 15, Ui.TEXT, true);
        TextView description = Ui.text(activity, descriptionText, 11, Ui.MUTED, false);
        description.setSingleLine(true);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(title);
        text.addView(description, matchWrap(6));
        LinearLayout.LayoutParams textParams = Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        textParams.leftMargin = Ui.dp(activity, 12);
        row.addView(text, textParams);
        TextView arrow = Ui.text(activity, "›", 25, Ui.MUTED, false);
        arrow.setGravity(Gravity.CENTER);
        row.addView(arrow, Ui.linearParams(Ui.dp(activity, 24), Ui.dp(activity, 44)));
        return row;
    }

    private Switch settingSwitch(String text, boolean checked) {
        Switch view = new Switch(activity);
        view.setText(text);
        view.setTextColor(Ui.TEXT);
        view.setTextSize(14);
        view.setChecked(checked);
        view.setPadding(
                Ui.dp(activity, 3),
                Ui.dp(activity, 7),
                Ui.dp(activity, 3),
                Ui.dp(activity, 7)
        );
        return view;
    }

    private TextView actionButton(String text, int textColor, int fillColor) {
        TextView view = Ui.button(activity, text, textColor, fillColor, 15);
        if (fillColor == Ui.SURFACE) {
            view.setBackground(Ui.roundedStroke(activity, fillColor, 15, Ui.BORDER, 1));
        }
        return view;
    }

    private TextView infoText(String text) {
        TextView view = Ui.text(activity, text, 12, Ui.MUTED, false);
        view.setLineSpacing(0, 1.2f);
        view.setPadding(
                Ui.dp(activity, 14),
                Ui.dp(activity, 12),
                Ui.dp(activity, 14),
                Ui.dp(activity, 12)
        );
        view.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 15, Ui.BORDER, 1));
        return view;
    }

    private void addSectionTitle(LinearLayout content, String text) {
        TextView title = Ui.text(activity, text, 13, Ui.TEXT, true);
        LinearLayout.LayoutParams params = matchWrap(content.getChildCount() == 0 ? 2 : 21);
        params.bottomMargin = Ui.dp(activity, 9);
        content.addView(title, params);
    }

    private void addInfoCard(LinearLayout content, String titleText, String bodyText) {
        LinearLayout card = Ui.vertical(activity);
        card.setPadding(
                Ui.dp(activity, 15),
                Ui.dp(activity, 13),
                Ui.dp(activity, 15),
                Ui.dp(activity, 13)
        );
        card.setBackground(Ui.roundedStroke(
                activity,
                Ui.GREEN_SURFACE,
                17,
                Color.rgb(212, 229, 217),
                1
        ));
        TextView title = Ui.text(activity, titleText, 16, Ui.GREEN_TEXT, true);
        TextView body = Ui.text(activity, bodyText, 12, Ui.MUTED, false);
        body.setLineSpacing(0, 1.2f);
        card.addView(title);
        card.addView(body, matchWrap(7));
        content.addView(card, matchWrap(0));
    }

    private TextView sourceLink(String label, String url) {
        TextView view = actionButton(label + "  ↗", Ui.TEXT, Ui.SURFACE);
        view.setTextSize(14);
        view.setOnClickListener(ignored -> activity.startActivity(
                new Intent(Intent.ACTION_VIEW, Uri.parse(url))
        ));
        return view;
    }

    private String reminderSummaryText() {
        String notification = ReminderScheduler.canPostNotifications(activity)
                ? "通知已开启"
                : "通知未开启";
        String exact = ReminderScheduler.canScheduleExactAlarms(activity)
                ? "准点提醒已开启"
                : "准点提醒可能延迟";
        return notification + " · " + exact;
    }

    private String reminderStatusText() {
        String notification = ReminderScheduler.canPostNotifications(activity)
                ? "通知：已开启（" + ReminderScheduler.notificationImportanceText(activity) + "）"
                : "通知：未开启";
        String exact = ReminderScheduler.canScheduleExactAlarms(activity)
                ? "准点提醒：已开启"
                : "准点提醒：未开启（可能延迟）";
        return notification
                + "\n提醒方式：" + ReminderScheduler.alertModeText(activity)
                + "\n" + exact;
    }

    private String backgroundReminderHint() {
        String manufacturer = Build.MANUFACTURER == null
                ? ""
                : Build.MANUFACTURER.toLowerCase(java.util.Locale.ROOT);
        if (manufacturer.contains("vivo") || manufacturer.contains("iqoo")) {
            return "立即通知成功、1 分钟定时提醒失败，表示系统禁止了后台唤醒。请为念日开启“自启动”，并在“耗电详情”中允许后台高耗电。";
        }
        return "先用立即通知验证通知展示，再测试 1 分钟定时提醒；若只有后者失败，请允许念日自启动和后台运行。";
    }

    private String statusText() {
        String checked = preferences.getString("last_authority_check", "");
        if (checked == null || checked.isEmpty()) {
            return "尚未联网校验；当前使用内置离线数据。";
        }
        boolean standard = preferences.getBoolean("standard_reachable", false);
        boolean observatory = preferences.getBoolean("observatory_reachable", false);
        String state = standard && observatory ? "来源可访问" : "部分来源不可访问";
        return state + " · 上次检查 " + checked;
    }

    private String versionText() {
        try {
            android.content.pm.PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0);
            long build = Build.VERSION.SDK_INT >= 28
                    ? info.getLongVersionCode()
                    : info.versionCode;
            return "版本 " + info.versionName + " · 构建 " + build;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "版本信息不可用";
        }
    }

    private void styleDialog() {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        android.view.WindowManager.LayoutParams attributes = window.getAttributes();
        attributes.dimAmount = 0.46f;
        window.setAttributes(attributes);
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenWidth - Ui.dp(activity, 28), Ui.dp(activity, 540));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(activity, topDp);
        return params;
    }

    private LinearLayout.LayoutParams matchHeight(int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, heightDp)
        );
        params.topMargin = Ui.dp(activity, topDp);
        return params;
    }
}

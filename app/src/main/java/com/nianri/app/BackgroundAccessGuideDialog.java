package com.nianri.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

final class BackgroundAccessGuideDialog {
    private BackgroundAccessGuideDialog() {
    }

    static void show(
            Activity activity,
            BackgroundAccessGuide guide,
            Runnable openPrimary,
            Runnable openAppInfo,
            Runnable openBatterySettings
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = Ui.vertical(activity);
        int side = Ui.dp(activity, 20);
        panel.setPadding(side, Ui.dp(activity, 20), side, Ui.dp(activity, 18));
        panel.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 25, Ui.BORDER, 1));

        TextView badge = Ui.text(activity, "已识别 · " + guide.brandName, 11, Ui.GREEN_TEXT, true);
        badge.setPadding(
                Ui.dp(activity, 10),
                Ui.dp(activity, 6),
                Ui.dp(activity, 10),
                Ui.dp(activity, 6)
        );
        badge.setBackground(Ui.rounded(activity, Ui.GREEN_SURFACE, 999));
        panel.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = Ui.text(activity, "让提醒在后台也能到达", 20, Ui.TEXT, true);
        panel.addView(title, matchWrap(activity, 13));
        TextView intro = Ui.text(
                activity,
                "下面的开关必须由手机主人手动开启，念日无法代替操作。请按顺序完成：",
                13,
                Ui.MUTED,
                false
        );
        intro.setLineSpacing(0, 1.18f);
        panel.addView(intro, matchWrap(activity, 8));

        for (int i = 0; i < guide.steps.length; i++) {
            LinearLayout step = Ui.horizontal(activity);
            step.setPadding(
                    Ui.dp(activity, 13),
                    Ui.dp(activity, 11),
                    Ui.dp(activity, 13),
                    Ui.dp(activity, 11)
            );
            step.setBackground(Ui.roundedStroke(
                    activity,
                    Color.rgb(248, 247, 243),
                    16,
                    Ui.BORDER,
                    1
            ));
            TextView number = Ui.text(activity, String.valueOf(i + 1), 13, Color.WHITE, true);
            number.setGravity(Gravity.CENTER);
            number.setBackground(Ui.rounded(activity, Ui.ACCENT, 999));
            step.addView(number, Ui.linearParams(Ui.dp(activity, 30), Ui.dp(activity, 30)));
            TextView instruction = Ui.text(activity, guide.steps[i], 14, Ui.TEXT, true);
            instruction.setLineSpacing(0, 1.12f);
            LinearLayout.LayoutParams instructionParams = Ui.weightedParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1
            );
            instructionParams.leftMargin = Ui.dp(activity, 11);
            step.addView(instruction, instructionParams);
            panel.addView(step, matchWrap(activity, i == 0 ? 15 : 8));
        }

        TextView note = Ui.text(
                activity,
                guide.note + "\n\n设置完成后回到念日，用“1 分钟后测试提醒”验证。",
                12,
                Ui.GREEN_TEXT,
                false
        );
        note.setLineSpacing(0, 1.18f);
        note.setPadding(
                Ui.dp(activity, 13),
                Ui.dp(activity, 11),
                Ui.dp(activity, 13),
                Ui.dp(activity, 11)
        );
        note.setBackground(Ui.rounded(activity, Ui.GREEN_SURFACE, 16));
        panel.addView(note, matchWrap(activity, 12));

        String primaryLabel = guide.hasDedicatedSettingsPage()
                ? "我知道了，去启动管理"
                : "我知道了，去应用设置";
        TextView primary = Ui.button(activity, primaryLabel, Color.WHITE, Ui.ACCENT, 15);
        primary.setOnClickListener(view -> {
            dialog.dismiss();
            openPrimary.run();
        });
        panel.addView(primary, matchHeight(activity, 50, 13));

        LinearLayout alternatives = Ui.horizontal(activity);
        TextView appInfo = Ui.button(activity, "应用信息", Ui.TEXT, Color.rgb(239, 237, 231), 14);
        TextView battery = Ui.button(activity, "电池设置", Ui.TEXT, Color.rgb(239, 237, 231), 14);
        appInfo.setOnClickListener(view -> {
            dialog.dismiss();
            openAppInfo.run();
        });
        battery.setOnClickListener(view -> {
            dialog.dismiss();
            openBatterySettings.run();
        });
        alternatives.addView(appInfo, Ui.weightedParams(Ui.dp(activity, 44), 1));
        LinearLayout.LayoutParams batteryParams = Ui.weightedParams(Ui.dp(activity, 44), 1);
        batteryParams.leftMargin = Ui.dp(activity, 8);
        alternatives.addView(battery, batteryParams);
        panel.addView(alternatives, matchWrap(activity, 8));

        TextView later = Ui.button(activity, "稍后设置", Ui.MUTED, Ui.SURFACE, 14);
        later.setOnClickListener(view -> dialog.dismiss());
        panel.addView(later, matchHeight(activity, 40, 5));

        dialog.setContentView(panel);
        dialog.show();
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

    private static LinearLayout.LayoutParams matchWrap(Activity activity, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(activity, topDp);
        return params;
    }

    private static LinearLayout.LayoutParams matchHeight(Activity activity, int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(activity, heightDp)
        );
        params.topMargin = Ui.dp(activity, topDp);
        return params;
    }
}

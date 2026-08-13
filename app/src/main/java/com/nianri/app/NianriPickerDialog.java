package com.nianri.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.BooleanSupplier;

final class NianriPickerDialog {
    private NianriPickerDialog() {
    }

    static void show(
            Activity activity,
            String titleText,
            String hintText,
            View pickerContent,
            BooleanSupplier onConfirmed
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        LinearLayout panel = Ui.vertical(activity);
        int side = Ui.dp(activity, 18);
        panel.setPadding(side, Ui.dp(activity, 17), side, Ui.dp(activity, 16));
        panel.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 23, Ui.BORDER, 1));

        TextView badge = Ui.text(activity, "念日", 11, Ui.GREEN_TEXT, true);
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

        TextView title = Ui.text(activity, titleText, 19, Ui.TEXT, true);
        panel.addView(title, matchWrap(activity, 11));
        if (hintText != null && !hintText.isEmpty()) {
            TextView hint = Ui.text(activity, hintText, 12, Ui.MUTED, false);
            hint.setLineSpacing(0, 1.16f);
            panel.addView(hint, matchWrap(activity, 6));
        }

        LinearLayout pickerCard = Ui.vertical(activity);
        pickerCard.setPadding(
                Ui.dp(activity, 6),
                Ui.dp(activity, 4),
                Ui.dp(activity, 6),
                Ui.dp(activity, 4)
        );
        pickerCard.setBackground(Ui.roundedStroke(
                activity,
                Ui.BACKGROUND,
                18,
                Ui.BORDER,
                1
        ));
        pickerCard.addView(pickerContent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        panel.addView(pickerCard, matchWrap(activity, 11));

        LinearLayout actions = Ui.horizontal(activity);
        TextView cancel = Ui.button(activity, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        TextView confirm = Ui.button(activity, "确定", Color.WHITE, Ui.ACCENT, 15);
        cancel.setOnClickListener(view -> dialog.dismiss());
        confirm.setOnClickListener(view -> {
            if (onConfirmed.getAsBoolean()) dialog.dismiss();
        });
        actions.addView(cancel, Ui.weightedParams(Ui.dp(activity, 45), 1));
        LinearLayout.LayoutParams confirmParams = Ui.weightedParams(Ui.dp(activity, 45), 1.25f);
        confirmParams.leftMargin = Ui.dp(activity, 9);
        actions.addView(confirm, confirmParams);
        panel.addView(actions, matchWrap(activity, 10));

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
        int width = Math.min(screenWidth - Ui.dp(activity, 40), Ui.dp(activity, 360));
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
}

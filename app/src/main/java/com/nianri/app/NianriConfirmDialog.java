package com.nianri.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class NianriConfirmDialog {
    private NianriConfirmDialog() {
    }

    public static void show(
            Activity activity,
            String titleText,
            String messageText,
            String confirmText,
            Runnable onConfirmed
    ) {
        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout panel = Ui.vertical(activity);
        int side = Ui.dp(activity, 20);
        panel.setPadding(side, Ui.dp(activity, 21), side, Ui.dp(activity, 20));
        panel.setBackground(Ui.roundedStroke(activity, Ui.SURFACE, 25, Ui.BORDER, 1));

        TextView badge = Ui.text(activity, "请确认", 11, Ui.ACCENT, true);
        badge.setPadding(
                Ui.dp(activity, 10),
                Ui.dp(activity, 6),
                Ui.dp(activity, 10),
                Ui.dp(activity, 6)
        );
        badge.setBackground(Ui.rounded(activity, Color.rgb(252, 239, 236), 999));
        panel.addView(badge, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView title = Ui.text(activity, titleText, 19, Ui.TEXT, true);
        panel.addView(title, matchWrap(activity, 14));

        TextView message = Ui.text(activity, messageText, 13, Ui.MUTED, false);
        message.setLineSpacing(0, 1.22f);
        message.setPadding(
                Ui.dp(activity, 14),
                Ui.dp(activity, 13),
                Ui.dp(activity, 14),
                Ui.dp(activity, 13)
        );
        message.setBackground(Ui.rounded(activity, Color.rgb(248, 247, 243), 15));
        panel.addView(message, matchWrap(activity, 11));

        LinearLayout actions = Ui.horizontal(activity);
        TextView cancel = Ui.button(activity, "取消", Ui.TEXT, Color.rgb(239, 237, 231), 15);
        TextView confirm = Ui.button(activity, confirmText, Color.WHITE, Ui.ACCENT, 15);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, Ui.dp(activity, 48), 1));
        LinearLayout.LayoutParams confirmParams = new LinearLayout.LayoutParams(
                0,
                Ui.dp(activity, 48),
                1.25f
        );
        confirmParams.leftMargin = Ui.dp(activity, 10);
        actions.addView(confirm, confirmParams);
        panel.addView(actions, matchWrap(activity, 18));

        cancel.setOnClickListener(view -> dialog.cancel());
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            onConfirmed.run();
        });

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
        int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
        int width = Math.min(screenWidth - Ui.dp(activity, 32), Ui.dp(activity, 500));
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

package com.nianri.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class Ui {
    public static final int BACKGROUND = Color.rgb(248, 247, 243);
    public static final int SURFACE = Color.WHITE;
    public static final int TEXT = Color.rgb(41, 41, 48);
    public static final int MUTED = Color.rgb(112, 111, 104);
    public static final int BORDER = Color.rgb(231, 228, 220);
    public static final int ACCENT = Color.rgb(223, 105, 93);
    public static final int GREEN_SURFACE = Color.rgb(230, 241, 233);
    public static final int GREEN_TEXT = Color.rgb(47, 108, 67);

    private Ui() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static TextView text(Context context, String value, float sp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(Typeface.create("sans", bold ? Typeface.BOLD : Typeface.NORMAL));
        view.setIncludeFontPadding(false);
        return view;
    }

    public static TextView button(Context context, String value, int textColor, int fillColor, float radiusDp) {
        TextView view = text(context, value, 14, textColor, true);
        view.setGravity(Gravity.CENTER);
        view.setClickable(true);
        view.setFocusable(true);
        view.setBackground(ripple(context, fillColor, radiusDp));
        view.setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10));
        return view;
    }

    public static GradientDrawable rounded(Context context, int fillColor, float radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fillColor);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable roundedStroke(
            Context context,
            int fillColor,
            float radiusDp,
            int strokeColor,
            float strokeDp
    ) {
        GradientDrawable drawable = rounded(context, fillColor, radiusDp);
        drawable.setStroke(dp(context, strokeDp), strokeColor);
        return drawable;
    }

    public static RippleDrawable ripple(Context context, int fillColor, float radiusDp) {
        GradientDrawable content = rounded(context, fillColor, radiusDp);
        return new RippleDrawable(
                ColorStateList.valueOf(Color.argb(36, 0, 0, 0)),
                content,
                null
        );
    }

    public static LinearLayout vertical(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public static LinearLayout horizontal(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public static LinearLayout.LayoutParams linearParams(int width, int height) {
        return new LinearLayout.LayoutParams(width, height);
    }

    public static LinearLayout.LayoutParams weightedParams(int height, float weight) {
        return new LinearLayout.LayoutParams(0, height, weight);
    }

    public static void margin(View view, int left, int top, int right, int bottom) {
        ViewGroup.LayoutParams raw = view.getLayoutParams();
        if (raw instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) raw;
            params.setMargins(left, top, right, bottom);
            view.setLayoutParams(params);
        }
    }
}

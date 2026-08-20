package com.nianri.app;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class PinDateWidgetActivity extends Activity {
    private boolean requestStarted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildLoadingView());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (requestStarted) {
            return;
        }
        requestStarted = true;
        getWindow().getDecorView().post(this::requestPinWidget);
    }

    private void requestPinWidget() {
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        if (!DateWidgetInstancePolicy.canRequestPin(DateWidgetProvider.activeWidgetIds(this))) {
            Toast.makeText(
                    this,
                    R.string.date_widget_already_added,
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }
        if (!manager.isRequestPinAppWidgetSupported()) {
            Toast.makeText(
                    this,
                    R.string.shortcut_widget_unsupported,
                    Toast.LENGTH_LONG
            ).show();
            finish();
            return;
        }

        try {
            boolean launched = manager.requestPinAppWidget(
                    new ComponentName(this, DateWidgetProvider.class),
                    null,
                    null
            );
            if (!launched) {
                Toast.makeText(
                        this,
                        R.string.shortcut_widget_unsupported,
                        Toast.LENGTH_LONG
                ).show();
            }
        } catch (RuntimeException error) {
            Toast.makeText(
                    this,
                    R.string.shortcut_widget_unavailable,
                    Toast.LENGTH_LONG
            ).show();
        }
        finish();
    }

    private View buildLoadingView() {
        LinearLayout root = Ui.vertical(this);
        root.setGravity(Gravity.CENTER);
        root.setPadding(
                Ui.dp(this, 28),
                Ui.dp(this, 28),
                Ui.dp(this, 28),
                Ui.dp(this, 28)
        );
        root.setBackgroundColor(Ui.BACKGROUND);

        TextView icon = Ui.text(this, "▣", 28, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(Ui.rounded(this, Ui.ACCENT, 16));
        root.addView(icon, Ui.linearParams(Ui.dp(this, 56), Ui.dp(this, 56)));

        TextView title = Ui.text(this, "正在打开桌面卡片…", 16, Ui.TEXT, true);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleParams = Ui.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        titleParams.topMargin = Ui.dp(this, 16);
        root.addView(title, titleParams);
        return root;
    }
}

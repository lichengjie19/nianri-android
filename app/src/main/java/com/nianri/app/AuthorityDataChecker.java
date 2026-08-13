package com.nianri.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AuthorityDataChecker {
    public interface Callback {
        void onComplete(boolean standardsReachable, boolean observatoryReachable, String checkedAt);
    }

    public static final String STANDARD_URL =
            "https://openstd.samr.gov.cn/bzgk/std/newGbInfo?hcno=E107EA4DE9725EDF819F33C60A44B296";
    public static final String OBSERVATORY_URL =
            "https://pmo.cas.cn/xwdt2019/kpdt2019/202203/P020241223526694003756.pdf";
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private AuthorityDataChecker() {
    }

    public static void check(Context context, Callback callback) {
        Context appContext = context.getApplicationContext();
        EXECUTOR.execute(() -> {
            boolean standard = reachable(STANDARD_URL);
            boolean observatory = reachable(OBSERVATORY_URL);
            String checkedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
            appContext.getSharedPreferences("nianri_settings", Context.MODE_PRIVATE)
                    .edit()
                    .putString("last_authority_check", checkedAt)
                    .putBoolean("standard_reachable", standard)
                    .putBoolean("observatory_reachable", observatory)
                    .apply();
            new Handler(Looper.getMainLooper()).post(() -> callback.onComplete(standard, observatory, checkedAt));
        });
    }

    private static boolean reachable(String value) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(value).openConnection();
            connection.setConnectTimeout(8000);
            connection.setReadTimeout(8000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "NianRi/0.1 Android");
            int code = connection.getResponseCode();
            return code >= 200 && code < 400;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}

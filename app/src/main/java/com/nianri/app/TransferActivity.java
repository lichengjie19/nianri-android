package com.nianri.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.window.OnBackInvokedDispatcher;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class TransferActivity extends Activity {
    private static final int REQUEST_SCAN = 2301;

    private LinearLayout content;
    private Runnable backAction;
    private NearbyTransferProtocol.ReceiverSession receiverSession;
    private int receiverGeneration;
    private volatile boolean destroyed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        setContentView(buildRoot());
        registerPredictiveBackIfSupported();
        showHome();
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        stopReceiver();
        super.onDestroy();
    }

    @Override
    @SuppressLint("GestureBackNavigation")
    public void onBackPressed() {
        handleBack();
    }

    private void handleBack() {
        if (backAction != null) {
            backAction.run();
        } else {
            finishAfterTransition();
        }
    }

    private void registerPredictiveBackIfSupported() {
        if (Build.VERSION.SDK_INT < 33) return;
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                this::handleBack
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SCAN || resultCode != RESULT_OK || data == null) return;
        String raw = data.getStringExtra(QrScannerActivity.EXTRA_PAIRING_CODE);
        try {
            showSendPreview(NearbyTransferProtocol.PairingInfo.parse(raw));
        } catch (IllegalArgumentException error) {
            Toast.makeText(this, "二维码已失效，请重新扫描", Toast.LENGTH_LONG).show();
        }
    }

    private View buildRoot() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(Ui.BACKGROUND);
        content = Ui.vertical(this);
        int side = Ui.dp(this, 20);
        content.setPadding(side, Ui.dp(this, 18), side, Ui.dp(this, 30));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private void showHome() {
        stopReceiver();
        startPage("换机迁移", null);
        infoCard(
                "不用账号，也不经过服务器",
                "两台手机连接同一 Wi-Fi 或其中一台开启的热点，通过一次性二维码建立端到端加密连接。"
        );

        EventStore store = new EventStore(this);
        int active = store.load().size();
        int deleted = store.loadDeleted().size();
        sectionTitle("选择这台手机的角色");
        TextView receive = actionButton("这是新手机 · 接收数据", Color.WHITE, Ui.ACCENT);
        receive.setContentDescription("在新手机上生成换机二维码");
        receive.setOnClickListener(view -> showReceive());
        add(receive, 52, 0);

        TextView send = actionButton("这是旧手机 · 发送数据", Ui.TEXT, Ui.SURFACE);
        send.setContentDescription("在旧手机上扫描换机二维码");
        send.setOnClickListener(view -> showSendIntro());
        add(send, 52, 10);

        sectionTitle("本机数据");
        addInfoText("当前有 " + active + " 个日期、" + deleted + " 个回收站日期。迁移会保留历法、提醒时间、备注和自定义标签。", 0);
        addInfoText("二维码 5 分钟后自动失效；每次只允许一台旧手机连接。传输完成后临时密钥立即作废。", 9);
    }

    private void showReceive() {
        stopReceiver();
        startPage("新手机接收", this::showHome);
        infoCard(
                "先保持这个页面打开",
                "让旧手机打开“换机迁移 → 旧手机发送”，扫描下方二维码。两台手机需要处于同一局域网。"
        );
        sectionTitle("连接二维码");

        TextView status = addInfoText("正在创建一次性加密连接…", 0);
        NearbyTransferProtocol.ReceiverSession session;
        try {
            session = NearbyTransferProtocol.createReceiver();
        } catch (IOException error) {
            status.setText(error.getMessage() == null
                    ? "无法获取局域网地址，请连接 Wi-Fi 或热点后重试"
                    : error.getMessage());
            TextView retry = actionButton("重新检测网络", Color.WHITE, Ui.ACCENT);
            retry.setOnClickListener(view -> showReceive());
            add(retry, 50, 10);
            return;
        }

        receiverSession = session;
        int generation = ++receiverGeneration;
        NearbyTransferProtocol.PairingInfo pairing = session.pairingInfo();
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int qrSize = Math.max(
                Ui.dp(this, 190),
                Math.min(Ui.dp(this, 270), screenWidth - Ui.dp(this, 84))
        );
        ImageView qr = new ImageView(this);
        qr.setImageBitmap(QrCodeRenderer.render(pairing.encode(), qrSize));
        qr.setAdjustViewBounds(true);
        qr.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        qr.setBackground(Ui.roundedStroke(this, Color.WHITE, 20, Ui.BORDER, 1));
        qr.setContentDescription("念日换机二维码，安全码 " + pairing.safetyCode());
        LinearLayout.LayoutParams qrParams = new LinearLayout.LayoutParams(qrSize, qrSize);
        qrParams.gravity = Gravity.CENTER_HORIZONTAL;
        qrParams.topMargin = Ui.dp(this, 14);
        content.addView(qr, qrParams);

        TextView code = Ui.text(this, "安全码  " + spacedCode(pairing.safetyCode()), 18, Ui.TEXT, true);
        code.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams codeParams = matchWrap(14);
        content.addView(code, codeParams);
        status.setText("等待旧手机扫码，二维码将在 5 分钟后失效");

        TextView regenerate = actionButton("刷新二维码", Ui.TEXT, Ui.SURFACE);
        regenerate.setOnClickListener(view -> showReceive());
        add(regenerate, 48, 14);

        session.start(new NearbyTransferProtocol.ReceiverCallback() {
            @Override
            public boolean onPayload(byte[] payload) {
                if (destroyed || generation != receiverGeneration) return false;
                final TransferData data;
                try {
                    data = TransferData.decode(payload);
                } catch (IllegalArgumentException error) {
                    runOnUiThread(() -> {
                        if (generation == receiverGeneration) {
                            status.setText(error.getMessage() == null
                                    ? "收到的数据无法验证，请让旧手机重新发送"
                                    : error.getMessage());
                        }
                    });
                    return false;
                }
                runOnUiThread(() -> {
                    if (!destroyed && generation == receiverGeneration) {
                        showReceivePreview(data);
                    }
                });
                return true;
            }

            @Override
            public void onFailure(String message) {
                runOnUiThread(() -> {
                    if (!destroyed && generation == receiverGeneration) {
                        status.setText(message);
                    }
                });
            }
        });
    }

    private void showSendIntro() {
        stopReceiver();
        startPage("旧手机发送", this::showHome);
        EventStore store = new EventStore(this);
        int active = store.load().size();
        int deleted = store.loadDeleted().size();
        infoCard(
                "先在新手机生成二维码",
                "新手机打开“换机迁移 → 新手机接收”并保持二维码页面。旧手机扫码后仍需确认才会发送。"
        );
        sectionTitle("准备发送");
        addInfoText("可发送 " + active + " 个日期和 " + deleted + " 个回收站日期。日期、备注、标签及每条提醒设置都会保留。", 0);

        TextView scan = actionButton("扫描新手机二维码", Color.WHITE, Ui.ACCENT);
        scan.setEnabled(active + deleted > 0);
        if (active + deleted == 0) {
            scan.setText("本机暂无可迁移数据");
            scan.setTextColor(Ui.MUTED);
            scan.setBackground(Ui.ripple(this, Color.rgb(239, 237, 231), 15));
        } else {
            scan.setOnClickListener(view -> startActivityForResult(
                    new Intent(this, QrScannerActivity.class),
                    REQUEST_SCAN
            ));
        }
        add(scan, 52, 10);
        addInfoText("相机只在扫码页面使用，画面不会保存或上传。", 10);
    }

    private void showSendPreview(NearbyTransferProtocol.PairingInfo pairing) {
        stopReceiver();
        startPage("确认发送", this::showSendIntro);
        EventStore store = new EventStore(this);
        int active = store.load().size();
        int deleted = store.loadDeleted().size();
        infoCard(
                "已连接到新手机",
                "请确认两台手机显示的安全码一致：" + spacedCode(pairing.safetyCode())
        );
        sectionTitle("迁移内容");
        TextView summary = addInfoText(summaryText(active, deleted, deleted > 0), 0);
        Switch includeDeleted = new Switch(this);
        includeDeleted.setText(String.format(Locale.CHINA, "同时迁移回收站（%d 个）", deleted));
        includeDeleted.setTextSize(14);
        includeDeleted.setTextColor(Ui.TEXT);
        includeDeleted.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        includeDeleted.setChecked(deleted > 0);
        includeDeleted.setEnabled(deleted > 0);
        content.addView(includeDeleted, matchWrap(8));
        includeDeleted.setOnCheckedChangeListener((button, checked) ->
                summary.setText(summaryText(active, deleted, checked)));

        TextView send = actionButton("加密发送到新手机", Color.WHITE, Ui.ACCENT);
        send.setOnClickListener(view -> sendData(pairing, includeDeleted.isChecked()));
        add(send, 52, 15);

        TextView rescan = actionButton("重新扫描", Ui.TEXT, Ui.SURFACE);
        rescan.setOnClickListener(view -> startActivityForResult(
                new Intent(this, QrScannerActivity.class),
                REQUEST_SCAN
        ));
        add(rescan, 48, 9);
    }

    private void sendData(NearbyTransferProtocol.PairingInfo pairing, boolean includeDeleted) {
        final byte[] payload;
        try {
            payload = TransferData.capture(this, includeDeleted).encode();
        } catch (RuntimeException error) {
            Toast.makeText(
                    this,
                    error.getMessage() == null ? "无法整理迁移数据" : error.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
            return;
        }
        startPage("正在发送", () -> Toast.makeText(
                this,
                "正在发送，请稍候",
                Toast.LENGTH_SHORT
        ).show());
        infoCard(
                "正在端到端加密传输",
                "请保持两台手机的念日页面打开，并暂时不要切换 Wi-Fi 或热点。"
        );
        TextView status = addInfoText("正在连接新手机…", 18);
        new Thread(() -> {
            NearbyTransferProtocol.SendResult result = NearbyTransferProtocol.send(pairing, payload);
            runOnUiThread(() -> {
                if (destroyed) return;
                if (result.success) {
                    showSendSuccess(payload.length);
                } else {
                    showSendFailure(pairing, result.message);
                }
            });
        }, "nianri-transfer-sender").start();
        status.setText("正在发送并等待新手机校验…");
    }

    private void showSendSuccess(int payloadBytes) {
        startPage("发送完成", null);
        infoCard(
                "新手机已安全收到数据",
                "本次共传输 " + readableSize(payloadBytes) + "。请在新手机上选择合并或替换，本机数据不会被删除。"
        );
        TextView done = actionButton("完成", Color.WHITE, Ui.ACCENT);
        done.setOnClickListener(view -> finish());
        add(done, 52, 22);
    }

    private void showSendFailure(
            NearbyTransferProtocol.PairingInfo pairing,
            String message
    ) {
        startPage("发送未完成", this::showSendIntro);
        addInfoText(message, 0);
        TextView retry = actionButton("使用当前二维码重试", Color.WHITE, Ui.ACCENT);
        retry.setOnClickListener(view -> showSendPreview(pairing));
        add(retry, 52, 12);
        TextView rescan = actionButton("重新扫描二维码", Ui.TEXT, Ui.SURFACE);
        rescan.setOnClickListener(view -> startActivityForResult(
                new Intent(this, QrScannerActivity.class),
                REQUEST_SCAN
        ));
        add(rescan, 48, 9);
    }

    private void showReceivePreview(TransferData data) {
        stopReceiver();
        startPage("确认接收", this::showHome);
        infoCard(
                "已收到并验证数据",
                "来源：" + data.sourceDevice + " · 念日 " + data.sourceVersion
                        + "\n发送时间：" + formatTime(data.exportedAt)
        );
        sectionTitle("迁移内容");
        addInfoText(
                data.events.size() + " 个日期 · "
                        + data.deletedEvents.size() + " 个回收站日期 · "
                        + data.tags.size() + " 个标签",
                0
        );
        addInfoText("合并会保留新手机现有数据，并按日期内容自动去重；同 ID 的不同标签会安全拆分。", 9);

        TextView merge = actionButton("合并并自动去重", Color.WHITE, Ui.ACCENT);
        merge.setOnClickListener(view -> applyIncoming(data, false));
        add(merge, 52, 15);

        TextView replace = actionButton("用旧手机数据完全替换", Ui.ACCENT, Color.rgb(252, 239, 236));
        replace.setOnClickListener(view -> NianriConfirmDialog.show(
                this,
                "替换新手机上的数据？",
                "新手机现有日期、标签和回收站将被本次迁移内容替换。旧手机数据不受影响。",
                "确认替换",
                () -> applyIncoming(data, true)
        ));
        add(replace, 50, 9);
    }

    private void applyIncoming(TransferData data, boolean replace) {
        startPage("正在导入", () -> Toast.makeText(
                this,
                "正在写入数据，请稍候",
                Toast.LENGTH_SHORT
        ).show());
        infoCard(
                replace ? "正在替换本机数据" : "正在合并并自动去重",
                "念日正在写入日期、标签和回收站，并重新登记提醒。"
        );
        new Thread(() -> {
            try {
                TransferMerger.Result result = TransferImporter.apply(
                        getApplicationContext(),
                        data,
                        replace
                );
                runOnUiThread(() -> {
                    if (destroyed) return;
                    ReminderScheduler.scheduleAll(this);
                    showImportSuccess(result);
                });
            } catch (RuntimeException error) {
                runOnUiThread(() -> {
                    if (!destroyed) showImportFailure(data, replace, error);
                });
            }
        }, "nianri-transfer-importer").start();
    }

    private void showImportSuccess(TransferMerger.Result result) {
        startPage("迁移完成", null);
        String body;
        if (result.replaced) {
            body = "已写入 " + result.importedEvents + " 个日期、"
                    + result.importedDeletedEvents + " 个回收站日期和 "
                    + result.tags.size() + " 个标签。";
        } else {
            body = "新增 " + result.importedEvents + " 个日期、"
                    + result.importedDeletedEvents + " 个回收站日期和 "
                    + result.importedTags + " 个标签；跳过 "
                    + result.duplicateEvents + " 个重复项。";
        }
        infoCard("数据已迁移到这台手机", body + " 所有有效提醒已重新登记。");
        TextView done = actionButton("返回念日首页", Color.WHITE, Ui.ACCENT);
        done.setOnClickListener(view -> finish());
        add(done, 52, 22);
    }

    private void showImportFailure(TransferData data, boolean replace, RuntimeException error) {
        startPage("导入未完成", this::showHome);
        addInfoText(
                error.getMessage() == null ? "写入本机数据时出现问题，请重试" : error.getMessage(),
                0
        );
        TextView retry = actionButton("重试导入", Color.WHITE, Ui.ACCENT);
        retry.setOnClickListener(view -> applyIncoming(data, replace));
        add(retry, 52, 12);
    }

    private void startPage(String titleText, Runnable childBackAction) {
        content.removeAllViews();
        backAction = childBackAction;
        LinearLayout header = Ui.horizontal(this);
        TextView back = Ui.button(this, "‹", Ui.TEXT, Color.rgb(236, 234, 228), 999);
        back.setTextSize(26);
        back.setContentDescription(childBackAction == null ? "关闭换机迁移" : "返回上一步");
        back.setOnClickListener(view -> {
            if (backAction != null) backAction.run();
            else finish();
        });
        header.addView(back, Ui.linearParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        TextView title = Ui.text(this, titleText, 22, Ui.TEXT, true);
        LinearLayout.LayoutParams titleParams = Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(title, titleParams);
        content.addView(header, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
    }

    private void infoCard(String titleText, String bodyText) {
        LinearLayout card = Ui.vertical(this);
        card.setPadding(
                Ui.dp(this, 16),
                Ui.dp(this, 15),
                Ui.dp(this, 16),
                Ui.dp(this, 15)
        );
        card.setBackground(Ui.roundedStroke(
                this,
                Ui.GREEN_SURFACE,
                19,
                Color.rgb(212, 229, 217),
                1
        ));
        TextView title = Ui.text(this, titleText, 16, Ui.GREEN_TEXT, true);
        TextView body = Ui.text(this, bodyText, 12, Ui.MUTED, false);
        body.setLineSpacing(0, 1.22f);
        card.addView(title);
        LinearLayout.LayoutParams bodyParams = matchWrap(8);
        card.addView(body, bodyParams);
        content.addView(card, matchWrap(18));
    }

    private void sectionTitle(String titleText) {
        TextView title = Ui.text(this, titleText, 13, Ui.TEXT, true);
        LinearLayout.LayoutParams params = matchWrap(22);
        params.bottomMargin = Ui.dp(this, 9);
        content.addView(title, params);
    }

    private TextView addInfoText(String value, int topDp) {
        TextView view = Ui.text(this, value, 13, Ui.MUTED, false);
        view.setLineSpacing(0, 1.20f);
        view.setPadding(
                Ui.dp(this, 14),
                Ui.dp(this, 13),
                Ui.dp(this, 14),
                Ui.dp(this, 13)
        );
        view.setBackground(Ui.roundedStroke(this, Ui.SURFACE, 16, Ui.BORDER, 1));
        content.addView(view, matchWrap(topDp));
        return view;
    }

    private TextView actionButton(String text, int textColor, int fillColor) {
        TextView button = Ui.button(this, text, textColor, fillColor, 15);
        if (fillColor == Ui.SURFACE) {
            button.setBackground(Ui.roundedStroke(this, fillColor, 15, Ui.BORDER, 1));
        }
        return button;
    }

    private void add(View view, int heightDp, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, heightDp)
        );
        params.topMargin = Ui.dp(this, topDp);
        content.addView(view, params);
    }

    private LinearLayout.LayoutParams matchWrap(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = Ui.dp(this, topDp);
        return params;
    }

    private void stopReceiver() {
        receiverGeneration++;
        NearbyTransferProtocol.ReceiverSession active = receiverSession;
        receiverSession = null;
        if (active != null) active.close();
    }

    private static String spacedCode(String value) {
        if (value == null || value.length() != 6) return value == null ? "" : value;
        return value.substring(0, 3) + " " + value.substring(3);
    }

    private static String summaryText(int active, int deleted, boolean includeDeleted) {
        return includeDeleted
                ? "将发送 " + active + " 个日期和 " + deleted + " 个回收站日期"
                : "将发送 " + active + " 个日期，不包含回收站";
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(new Date(millis));
    }

    private static String readableSize(int bytes) {
        if (bytes < 1024) return bytes + " B";
        return String.format(Locale.CHINA, "%.1f KB", bytes / 1024f);
    }
}

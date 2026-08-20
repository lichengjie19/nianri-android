package com.nianri.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Paint;
import android.graphics.RectF;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("deprecation")
public final class QrScannerActivity extends Activity
        implements SurfaceHolder.Callback, Camera.PreviewCallback {
    static final String EXTRA_PAIRING_CODE = "pairing_code";
    private static final int REQUEST_CAMERA = 2201;
    private static final long DECODE_INTERVAL_MS = 280L;

    private final ExecutorService decoder = Executors.newSingleThreadExecutor();
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private final AtomicBoolean completed = new AtomicBoolean(false);
    private SurfaceView preview;
    private TextView status;
    private TextView permissionAction;
    private Camera camera;
    private int cameraId = -1;
    private long lastDecodeAt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Ui.BACKGROUND);
        getWindow().setNavigationBarColor(Ui.BACKGROUND);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildContent());
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
        }
    }

    private View buildContent() {
        LinearLayout root = Ui.vertical(this);
        root.setBackgroundColor(Ui.BACKGROUND);
        int side = Ui.dp(this, 20);
        root.setPadding(side, Ui.dp(this, 18), side, Ui.dp(this, 20));

        LinearLayout header = Ui.horizontal(this);
        TextView back = Ui.button(this, "‹", Ui.TEXT, Color.rgb(236, 234, 228), 999);
        back.setTextSize(26);
        back.setContentDescription("返回换机迁移");
        back.setOnClickListener(view -> finish());
        header.addView(back, Ui.linearParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        TextView title = Ui.text(this, "扫描新手机二维码", 21, Ui.TEXT, true);
        LinearLayout.LayoutParams titleParams = Ui.weightedParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1
        );
        titleParams.leftMargin = Ui.dp(this, 12);
        header.addView(title, titleParams);
        root.addView(header, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView hint = Ui.text(
                this,
                "把二维码完整放入取景框。识别成功后会先显示待发送数量，不会立即传输。",
                13,
                Ui.MUTED,
                false
        );
        hint.setLineSpacing(0, 1.18f);
        LinearLayout.LayoutParams hintParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = Ui.dp(this, 15);
        root.addView(hint, hintParams);

        FrameLayout cameraFrame = new FrameLayout(this);
        cameraFrame.setBackground(Ui.rounded(this, Color.BLACK, 22));
        cameraFrame.setClipToOutline(true);
        preview = new SurfaceView(this);
        preview.getHolder().addCallback(this);
        cameraFrame.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        cameraFrame.addView(new ScannerOverlay(this), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        LinearLayout.LayoutParams frameParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        frameParams.topMargin = Ui.dp(this, 18);
        frameParams.bottomMargin = Ui.dp(this, 16);
        root.addView(cameraFrame, frameParams);

        status = Ui.text(this, "正在准备相机…", 13, Ui.MUTED, false);
        status.setGravity(Gravity.CENTER);
        root.addView(status, Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 32)
        ));

        permissionAction = Ui.button(this, "打开相机权限设置", Ui.ACCENT, Color.rgb(252, 239, 236), 15);
        permissionAction.setVisibility(View.GONE);
        permissionAction.setOnClickListener(view -> {
            Intent intent = new Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())
            );
            startActivity(intent);
        });
        LinearLayout.LayoutParams permissionParams = Ui.linearParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 48)
        );
        permissionParams.topMargin = Ui.dp(this, 8);
        root.addView(permissionAction, permissionParams);
        return root;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                && preview != null
                && preview.getHolder().getSurface().isValid()) {
            openCamera();
        }
    }

    @Override
    protected void onPause() {
        releaseCamera();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        decoder.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera == null) return;
        try {
            camera.stopPreview();
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
            camera.startPreview();
        } catch (IOException | RuntimeException error) {
            showCameraError();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        releaseCamera();
    }

    @Override
    public void onPreviewFrame(byte[] data, Camera sourceCamera) {
        if (completed.get() || data == null || sourceCamera == null) return;
        long now = System.currentTimeMillis();
        if (now - lastDecodeAt < DECODE_INTERVAL_MS || !decoding.compareAndSet(false, true)) return;
        lastDecodeAt = now;
        Camera.Size size;
        try {
            size = sourceCamera.getParameters().getPreviewSize();
        } catch (RuntimeException error) {
            decoding.set(false);
            return;
        }
        byte[] frame = data.clone();
        try {
            decoder.execute(() -> decodeFrame(frame, size.width, size.height));
        } catch (RejectedExecutionException ignored) {
            decoding.set(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CAMERA) return;
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            permissionAction.setVisibility(View.GONE);
            status.setText("正在准备相机…");
            if (preview.getHolder().getSurface().isValid()) openCamera();
        } else {
            status.setText("需要相机权限才能扫描迁移二维码");
            permissionAction.setVisibility(View.VISIBLE);
        }
    }

    private void openCamera() {
        if (camera != null || completed.get()) return;
        cameraId = findBackCamera();
        if (cameraId < 0) {
            status.setText("这台手机没有可用相机");
            return;
        }
        try {
            camera = Camera.open(cameraId);
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size previewSize = choosePreviewSize(parameters.getSupportedPreviewSizes());
            if (previewSize != null) parameters.setPreviewSize(previewSize.width, previewSize.height);
            List<Integer> previewFormats = parameters.getSupportedPreviewFormats();
            if (previewFormats != null && previewFormats.contains(ImageFormat.NV21)) {
                parameters.setPreviewFormat(ImageFormat.NV21);
            }
            List<String> focusModes = parameters.getSupportedFocusModes();
            if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }
            camera.setParameters(parameters);
            camera.setDisplayOrientation(displayOrientation(cameraId));
            camera.setPreviewDisplay(preview.getHolder());
            camera.setPreviewCallback(this);
            camera.startPreview();
            status.setText("等待识别迁移二维码");
            permissionAction.setVisibility(View.GONE);
        } catch (IOException | RuntimeException error) {
            releaseCamera();
            showCameraError();
        }
    }

    private void releaseCamera() {
        Camera current = camera;
        camera = null;
        if (current == null) return;
        try {
            current.setPreviewCallback(null);
            current.stopPreview();
        } catch (RuntimeException ignored) {
        }
        current.release();
    }

    private void decodeFrame(byte[] data, int width, int height) {
        try {
            PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                    data,
                    width,
                    height,
                    0,
                    0,
                    width,
                    height,
                    false
            );
            Result result;
            try {
                result = decode(source);
            } catch (NotFoundException ignored) {
                result = decode(source.invert());
            }
            String raw = result.getText();
            try {
                NearbyTransferProtocol.PairingInfo.parse(raw);
            } catch (IllegalArgumentException error) {
                runOnUiThread(() -> status.setText("识别到了二维码，但它不是念日迁移码"));
                return;
            }
            if (!completed.compareAndSet(false, true)) return;
            runOnUiThread(() -> {
                releaseCamera();
                Intent resultIntent = new Intent();
                resultIntent.putExtra(EXTRA_PAIRING_CODE, raw);
                setResult(RESULT_OK, resultIntent);
                finish();
            });
        } catch (NotFoundException ignored) {
            // Most preview frames contain no QR code.
        } catch (RuntimeException ignored) {
            // Camera buffers can change while the surface is being recreated.
        } finally {
            decoding.set(false);
        }
    }

    private Result decode(LuminanceSource source) throws NotFoundException {
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Collections.singletonList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        hints.put(DecodeHintType.CHARACTER_SET, "UTF-8");
        MultiFormatReader reader = new MultiFormatReader();
        reader.setHints(hints);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            return reader.decodeWithState(bitmap);
        } finally {
            reader.reset();
        }
    }

    private int findBackCamera() {
        int fallback = Camera.getNumberOfCameras() > 0 ? 0 : -1;
        Camera.CameraInfo info = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i;
        }
        return fallback;
    }

    private Camera.Size choosePreviewSize(List<Camera.Size> sizes) {
        if (sizes == null || sizes.isEmpty()) return null;
        Camera.Size best = sizes.get(0);
        long target = 960L * 540L;
        long bestDifference = Math.abs((long) best.width * best.height - target);
        for (Camera.Size size : sizes) {
            long pixels = (long) size.width * size.height;
            long difference = Math.abs(pixels - target);
            if (difference < bestDifference) {
                best = size;
                bestDifference = difference;
            }
        }
        return best;
    }

    private int displayOrientation(int id) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(id, info);
        int degrees;
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        if (rotation == Surface.ROTATION_90) degrees = 90;
        else if (rotation == Surface.ROTATION_180) degrees = 180;
        else if (rotation == Surface.ROTATION_270) degrees = 270;
        else degrees = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            return (360 - ((info.orientation + degrees) % 360)) % 360;
        }
        return (info.orientation - degrees + 360) % 360;
    }

    private void showCameraError() {
        status.setText("无法打开相机，请关闭其他相机应用后重试");
    }

    private static final class ScannerOverlay extends View {
        private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF frame = new RectF();

        ScannerOverlay(Activity activity) {
            super(activity);
            shade.setColor(Color.argb(115, 0, 0, 0));
            border.setColor(Color.WHITE);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Ui.dp(activity, 3));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float size = Math.min(getWidth(), getHeight()) * 0.70f;
            float left = (getWidth() - size) / 2f;
            float top = (getHeight() - size) / 2f;
            float right = left + size;
            float bottom = top + size;
            canvas.drawRect(0, 0, getWidth(), top, shade);
            canvas.drawRect(0, bottom, getWidth(), getHeight(), shade);
            canvas.drawRect(0, top, left, bottom, shade);
            canvas.drawRect(right, top, getWidth(), bottom, shade);
            frame.set(left, top, right, bottom);
            canvas.drawRoundRect(frame, 24f, 24f, border);
        }
    }
}

package com.xiao.safecamlite;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.PowerManager;
import android.util.Size;

import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleService;

import com.google.common.util.concurrent.ListenableFuture;
import fi.iki.elonen.NanoHTTPD;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraStreamService extends LifecycleService {
    public static final String ACTION_START = "com.xiao.safecamlite.START";
    public static final String ACTION_STOP = "com.xiao.safecamlite.STOP";
    public static final String EXTRA_PORT = "port";
    public static final String EXTRA_PIN = "pin";

    private static final String CHANNEL_ID = "safe_cam_channel";
    private static final int NOTIFICATION_ID = 1001;

    private ExecutorService cameraExecutor;
    private LocalHttpServer server;
    private ProcessCameraProvider cameraProvider;
    private PowerManager.WakeLock wakeLock;
    private long lastFrameMs = 0L;

    @Override
    public void onCreate() {
        super.onCreate();
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return Service.START_NOT_STICKY;
        }

        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 8080) : 8080;
        String pin = intent != null ? intent.getStringExtra(EXTRA_PIN) : "123456";
        if (pin == null || pin.trim().length() < 4) pin = "123456";

        startAsForeground();
        acquireWakeLock();
        startServer(port, pin);
        startCamera();

        return Service.START_STICKY;
    }

    private void startAsForeground() {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("SafeCam Lite is running")
                .setContentText("Camera stream is ON. Tap Stop in the app to end.")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    "SafeCam Lite running status",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && wakeLock == null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SafeCamLite:StreamLock");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(12 * 60 * 60 * 1000L);
            }
        } catch (Exception ignored) {}
    }

    private void startServer(int port, String pin) {
        if (server != null) return;
        server = new LocalHttpServer(port, pin);
        try {
            server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        } catch (IOException e) {
            stopSelf();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeFrame);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        analysis
                );
            } catch (Exception e) {
                stopSelf();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeFrame(ImageProxy image) {
        try {
            long now = System.currentTimeMillis();
            // About 2 FPS: stable for old Android phones and lower heat.
            if (now - lastFrameMs >= 500) {
                lastFrameMs = now;
                byte[] jpeg = YuvToJpeg.convert(image, 65);
                FrameStore.latestJpeg.set(jpeg);
            }
        } catch (Exception ignored) {
        } finally {
            image.close();
        }
    }

    @Override
    public void onDestroy() {
        if (cameraProvider != null) cameraProvider.unbindAll();
        if (server != null) {
            server.stop();
            server = null;
        }
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        if (cameraExecutor != null) cameraExecutor.shutdownNow();
        FrameStore.latestJpeg.set(null);
        super.onDestroy();
    }
}

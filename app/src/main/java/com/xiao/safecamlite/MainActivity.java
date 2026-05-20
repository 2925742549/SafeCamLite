package com.xiao.safecamlite;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.net.Inet4Address;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private static final int REQ = 44;
    private TextView status;
    private EditText pinInput;
    private EditText portInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(36, 42, 36, 36);
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("SafeCam Lite");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("旧安卓手机监控端。启动后请保持充电和联网，另一台手机用浏览器打开下方地址查看。");
        desc.setTextSize(15);
        desc.setPadding(0, 20, 0, 20);
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        pinInput = new EditText(this);
        pinInput.setHint("访问 PIN，至少 4 位，例如 8392");
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setText("123456");
        root.addView(pinInput, new LinearLayout.LayoutParams(-1, -2));

        portInput = new EditText(this);
        portInput.setHint("端口，例如 8080");
        portInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        portInput.setText("8080");
        root.addView(portInput, new LinearLayout.LayoutParams(-1, -2));

        Button start = new Button(this);
        start.setText("Start Camera Server");
        root.addView(start, new LinearLayout.LayoutParams(-1, -2));

        Button stop = new Button(this);
        stop.setText("Stop");
        root.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        Button battery = new Button(this);
        battery.setText("打开电池设置，关闭省电限制");
        root.addView(battery, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 22, 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        setContentView(root);
        updateStatus();

        start.setOnClickListener(v -> {
            if (!hasPermissions()) {
                requestNeededPermissions();
                return;
            }
            startServiceNow();
        });

        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, CameraStreamService.class);
            i.setAction(CameraStreamService.ACTION_STOP);
            ContextCompat.startForegroundService(this, i);
            status.setText("已发送停止指令。");
        });

        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
    }

    private boolean hasPermissions() {
        boolean cam = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean notifyOk = true;
        if (Build.VERSION.SDK_INT >= 33) {
            notifyOk = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return cam && notifyOk;
    }

    private void requestNeededPermissions() {
        List<String> ps = new ArrayList<>();
        ps.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= 33) ps.add(Manifest.permission.POST_NOTIFICATIONS);
        ActivityCompat.requestPermissions(this, ps.toArray(new String[0]), REQ);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ && hasPermissions()) {
            startServiceNow();
        } else {
            status.setText("需要允许摄像头权限，否则无法作为监控摄像头使用。");
        }
    }

    private void startServiceNow() {
        int port = 8080;
        try { port = Integer.parseInt(portInput.getText().toString().trim()); } catch (Exception ignored) {}
        String pin = pinInput.getText().toString().trim();
        if (pin.length() < 4) pin = "123456";

        Intent i = new Intent(this, CameraStreamService.class);
        i.setAction(CameraStreamService.ACTION_START);
        i.putExtra(CameraStreamService.EXTRA_PORT, port);
        i.putExtra(CameraStreamService.EXTRA_PIN, pin);
        ContextCompat.startForegroundService(this, i);

        status.setText(buildStatusText(port, pin));
    }

    private void updateStatus() {
        int port = 8080;
        try { port = Integer.parseInt(portInput != null ? portInput.getText().toString().trim() : "8080"); } catch (Exception ignored) {}
        String pin = pinInput != null ? pinInput.getText().toString().trim() : "123456";
        status.setText(buildStatusText(port, pin));
    }

    private String buildStatusText(int port, String pin) {
        List<String> ips = getLocalIpv4Addresses();
        StringBuilder sb = new StringBuilder();
        sb.append("查看地址：\n");
        if (ips.isEmpty()) {
            sb.append("请先连接 Wi-Fi，再点击 Start。\n");
        } else {
            for (String ip : ips) {
                sb.append("http://").append(ip).append(":").append(port).append("/?pin=").append(pin).append("\n");
            }
        }
        sb.append("\n安全建议：只在自己的场所使用；不要直接公网映射端口；外网查看建议用 Tailscale / ZeroTier 组网。");
        return sb.toString();
    }

    private List<String> getLocalIpv4Addresses() {
        List<String> result = new ArrayList<>();
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return result;
            for (Network network : cm.getAllNetworks()) {
                LinkProperties lp = cm.getLinkProperties(network);
                if (lp == null) continue;
                for (LinkAddress addr : lp.getLinkAddresses()) {
                    if (addr.getAddress() instanceof Inet4Address && !addr.getAddress().isLoopbackAddress()) {
                        result.add(addr.getAddress().getHostAddress());
                    }
                }
            }
        } catch (Exception ignored) {}
        return result;
    }
}

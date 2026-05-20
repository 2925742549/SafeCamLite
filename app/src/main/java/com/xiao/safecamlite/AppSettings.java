package com.xiao.safecamlite;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private CheckBox recordingCheck;
    private EditText recordSecondsInput;
    private EditText retentionHoursInput;
    private EditText maxStorageInput;
    private CheckBox autoStartCheck;

    private CheckBox nightModeCheck;
    private CheckBox torchCheck;
    private CheckBox nightGrayCheck;
    private EditText nightBrightnessInput;
    private EditText nightContrastInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(34, 36, 34, 36);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("SafeCam Lite Pro Night");
        title.setTextSize(26);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(-1, -2));

        TextView desc = new TextView(this);
        desc.setText("旧安卓手机监控端：实时观看、循环记录、网页回放、夜间低光增强、手电补光、开机自启、外出观看。请只用于自有场所和明确授权环境。");
        desc.setTextSize(15);
        desc.setPadding(0, 18, 0, 18);
        root.addView(desc, new LinearLayout.LayoutParams(-1, -2));

        pinInput = input("访问 PIN，至少 4 位，例如 8392", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        portInput = input("端口，例如 8080", InputType.TYPE_CLASS_NUMBER);

        recordingCheck = new CheckBox(this);
        recordingCheck.setText("开启循环记录 / 回放");
        recordSecondsInput = input("记录间隔秒数，建议旧手机 3-10 秒", InputType.TYPE_CLASS_NUMBER);
        retentionHoursInput = input("保留小时数，例如 24 / 72 / 168", InputType.TYPE_CLASS_NUMBER);
        maxStorageInput = input("最大占用空间 MB，例如 1024", InputType.TYPE_CLASS_NUMBER);

        autoStartCheck = new CheckBox(this);
        autoStartCheck.setText("开机后自动启动监控服务");

        nightModeCheck = new CheckBox(this);
        nightModeCheck.setText("开启夜间低光增强（数字提亮，不是真红外）");

        torchCheck = new CheckBox(this);
        torchCheck.setText("夜间同时打开手电筒补光（更清楚但耗电发热）");

        nightGrayCheck = new CheckBox(this);
        nightGrayCheck.setText("夜间黑白增强模式（弱光下更容易看清轮廓）");

        nightBrightnessInput = input("夜间提亮强度 0-100，建议 35-60", InputType.TYPE_CLASS_NUMBER);
        nightContrastInput = input("夜间对比增强 0-100，建议 20-40", InputType.TYPE_CLASS_NUMBER);

        root.addView(label("访问密码 PIN"));
        root.addView(pinInput);
        root.addView(label("端口"));
        root.addView(portInput);

        root.addView(recordingCheck);
        root.addView(label("循环记录间隔，越小越像录像，但越占空间"));
        root.addView(recordSecondsInput);
        root.addView(label("最多保留多少小时"));
        root.addView(retentionHoursInput);
        root.addView(label("最多占用多少 MB"));
        root.addView(maxStorageInput);

        root.addView(autoStartCheck);

        root.addView(label("夜视/弱光增强"));
        root.addView(nightModeCheck);
        root.addView(torchCheck);
        root.addView(nightGrayCheck);
        root.addView(label("夜间提亮强度"));
        root.addView(nightBrightnessInput);
        root.addView(label("夜间对比增强"));
        root.addView(nightContrastInput);

        Button save = new Button(this);
        save.setText("保存设置");
        root.addView(save, new LinearLayout.LayoutParams(-1, -2));

        Button start = new Button(this);
        start.setText("START CAMERA SERVER");
        root.addView(start, new LinearLayout.LayoutParams(-1, -2));

        Button stop = new Button(this);
        stop.setText("STOP");
        root.addView(stop, new LinearLayout.LayoutParams(-1, -2));

        Button battery = new Button(this);
        battery.setText("打开电池设置，关闭省电限制");
        root.addView(battery, new LinearLayout.LayoutParams(-1, -2));

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 20, 0, 0);
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        setContentView(scroll);
        loadSettingsToUi();
        updateStatus();

        save.setOnClickListener(v -> {
            saveSettings();
            updateStatus();
        });

        start.setOnClickListener(v -> {
            saveSettings();
            if (!hasPermissions()) {
                requestNeededPermissions();
                return;
            }
            startServiceNow();
        });

        stop.setOnClickListener(v -> {
            Intent i = new Intent(this, CameraStreamService.class);
            i.setAction(CameraStreamService.ACTION_STOP);
            startService(i);
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

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(14);
        t.setPadding(0, 12, 0, 2);
        return t;
    }

    private EditText input(String hint, int type) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        return e;
    }

    private void loadSettingsToUi() {
        pinInput.setText(AppSettings.pin(this));
        portInput.setText(String.valueOf(AppSettings.port(this)));

        recordingCheck.setChecked(AppSettings.recordingEnabled(this));
        recordSecondsInput.setText(String.valueOf(AppSettings.recordSeconds(this)));
        retentionHoursInput.setText(String.valueOf(AppSettings.retentionHours(this)));
        maxStorageInput.setText(String.valueOf(AppSettings.maxStorageMb(this)));

        autoStartCheck.setChecked(AppSettings.autoStart(this));

        nightModeCheck.setChecked(AppSettings.nightMode(this));
        torchCheck.setChecked(AppSettings.torchEnabled(this));
        nightGrayCheck.setChecked(AppSettings.nightGray(this));
        nightBrightnessInput.setText(String.valueOf(AppSettings.nightBrightness(this)));
        nightContrastInput.setText(String.valueOf(AppSettings.nightContrast(this)));
    }

    private void saveSettings() {
        String pin = pinInput.getText().toString().trim();
        if (pin.length() < 4) pin = "123456";

        int port = parseInt(portInput.getText().toString(), 8080);
        int sec = parseInt(recordSecondsInput.getText().toString(), 3);
        int hours = parseInt(retentionHoursInput.getText().toString(), 24);
        int mb = parseInt(maxStorageInput.getText().toString(), 1024);

        int nightBrightness = parseInt(nightBrightnessInput.getText().toString(), 45);
        int nightContrast = parseInt(nightContrastInput.getText().toString(), 25);

        SharedPreferences.Editor e = AppSettings.prefs(this).edit();
        e.putString(AppSettings.KEY_PIN, pin);
        e.putInt(AppSettings.KEY_PORT, AppSettings.clamp(port, 1024, 65535));

        e.putBoolean(AppSettings.KEY_RECORDING, recordingCheck.isChecked());
        e.putInt(AppSettings.KEY_RECORD_SECONDS, AppSettings.clamp(sec, 1, 60));
        e.putInt(AppSettings.KEY_RETENTION_HOURS, AppSettings.clamp(hours, 1, 24 * 30));
        e.putInt(AppSettings.KEY_MAX_STORAGE_MB, AppSettings.clamp(mb, 100, 1024 * 50));

        e.putBoolean(AppSettings.KEY_AUTO_START, autoStartCheck.isChecked());

        e.putBoolean(AppSettings.KEY_NIGHT_MODE, nightModeCheck.isChecked());
        e.putBoolean(AppSettings.KEY_TORCH, torchCheck.isChecked());
        e.putBoolean(AppSettings.KEY_NIGHT_GRAY, nightGrayCheck.isChecked());
        e.putInt(AppSettings.KEY_NIGHT_BRIGHTNESS, AppSettings.clamp(nightBrightness, 0, 100));
        e.putInt(AppSettings.KEY_NIGHT_CONTRAST, AppSettings.clamp(nightContrast, 0, 100));
        e.apply();
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return fallback; }
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
            status.setText("需要允许摄像头权限和通知权限，否则无法长期运行。");
        }
    }

    private void startServiceNow() {
        Intent i = new Intent(this, CameraStreamService.class);
        i.setAction(CameraStreamService.ACTION_START);
        ContextCompat.startForegroundService(this, i);
        status.setText(buildStatusText());
    }

    private void updateStatus() {
        status.setText(buildStatusText());
    }

    private String buildStatusText() {
        int port = AppSettings.port(this);
        String pin = AppSettings.pin(this);
        List<String> ips = getLocalIpv4Addresses();

        StringBuilder sb = new StringBuilder();
        sb.append("实时查看地址：\n");
        if (ips.isEmpty()) {
            sb.append("请先连接 Wi-Fi / Tailscale / ZeroTier，再点击 START。\n");
        } else {
            for (String ip : ips) {
                sb.append("http://").append(ip).append(":").append(port).append("/?pin=").append(pin).append("\n");
            }
        }

        sb.append("\n回放地址：\n");
        if (!ips.isEmpty()) {
            for (String ip : ips) {
                sb.append("http://").append(ip).append(":").append(port).append("/recordings?pin=").append(pin).append("\n");
            }
        }

        sb.append("\n循环记录：").append(AppSettings.recordingEnabled(this) ? "开启" : "关闭");
        sb.append(" ｜ 间隔：").append(AppSettings.recordSeconds(this)).append(" 秒");
        sb.append(" ｜ 保留：").append(AppSettings.retentionHours(this)).append(" 小时");
        sb.append(" ｜ 上限：").append(AppSettings.maxStorageMb(this)).append(" MB");
        sb.append("\n已用空间：").append(RecordingManager.humanSize(RecordingManager.totalBytes(this)));

        sb.append("\n夜间增强：").append(AppSettings.nightMode(this) ? "开启" : "关闭");
        sb.append(" ｜ 手电补光：").append(AppSettings.torchEnabled(this) ? "开启" : "关闭");
        sb.append(" ｜ 提亮：").append(AppSettings.nightBrightness(this));
        sb.append(" ｜ 对比：").append(AppSettings.nightContrast(this));

        sb.append("\n\n外出观看：推荐两台手机都装 Tailscale / ZeroTier。旧手机连上后，如果上面出现 100.x.x.x 这类虚拟 IP，就在外出手机浏览器打开对应地址。");
        sb.append("\n\n长期运行：插电、关闭省电限制、允许后台运行、不要直接公网映射端口。");

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

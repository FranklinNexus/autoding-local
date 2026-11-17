package org.autoding.research;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.UUID;

public final class MainActivity extends Activity {
    private TextView report;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout column = new LinearLayout(this); column.setOrientation(LinearLayout.VERTICAL); column.setPadding(24, 32, 24, 32);
        scroll.addView(column); setContentView(scroll);
        TextView title = new TextView(this); title.setText("AutoDing 能力研究"); title.setTextSize(24); column.addView(title);
        TextView detail = new TextView(this);
        detail.setText("手机独立运行实验：定时唤醒、一次 PIN 解锁、打开钉钉、本机识别回执。一次测试结束即停止。日常定时仍由原 App 管理。更新下班测试会实际操作今天的下班卡。");
        column.addView(detail);
        add(column, "刷新能力报告", this::refresh);
        add(column, "设置本机加密 PIN", () -> startActivity(new Intent(this, PinSetupActivity.class)));
        add(column, "60 秒后独立读取考勤（不点击）", () -> scheduleTrial(false));
        add(column, "60 秒后测试下班 / 更新下班卡", () -> new android.app.AlertDialog.Builder(this)
            .setTitle("一次下班实测")
            .setMessage("手机会在 60 秒后尝试解锁、打开考勤页并点击一次下班打卡。现在操作可能产生早退记录；当前已有下班卡时会尝试更新。结束后请核对钉钉记录。")
            .setNegativeButton("取消", null).setPositiveButton("安排本次实测", (d,w) -> scheduleTrial(true)).show());
        add(column, "取消本次独立测试", () -> { OneShotRunner.cancel(this); refresh(); });
        add(column, "已接充电头：3 分钟后测试下班", () -> new android.app.AlertDialog.Builder(this)
            .setTitle("接充电头后的单次实测")
            .setMessage("请先拔掉电脑 USB 并接充电头，再确认。确认后直接安排 3 分钟后的手机系统闹钟；请按电源键锁屏息屏，6 分钟后再接回电脑。本次会实际更新下班卡。")
            .setNegativeButton("取消", null).setPositiveButton("开始 3 分钟倒计时", (d,w) -> {
                try {
                    Intent usb=registerReceiver(null,new android.content.IntentFilter("android.hardware.usb.action.USB_STATE"));
                    Intent battery=registerReceiver(null,new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int source=battery == null ? -1 : battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED,-1);
                    String reason=TrialStartGuard.rejection(true,usb != null,
                        usb != null && usb.getBooleanExtra("connected",false),source,true,false);
                    if (!reason.isEmpty()) {
                        new android.app.AlertDialog.Builder(this).setTitle("尚未安排")
                            .setMessage("请确认已拔掉电脑 USB 并接充电头。检测结果："+reason).setPositiveButton("知道了",null).show();
                        return;
                    }
                    OneShotRunner.schedule(this,true,180,true); refresh();
                    new android.app.AlertDialog.Builder(this).setTitle("已安排 3 分钟后测试")
                        .setMessage("现在请按电源键锁屏息屏；继续接充电头，6 分钟后再接回电脑。")
                        .setPositiveButton("知道了",null).show();
                } catch (Exception ignored) {
                    android.widget.Toast.makeText(this,"尚未安排，请检查闹钟权限或当前任务。",android.widget.Toast.LENGTH_LONG).show();
                }
            }).show());
        add(column, "打开无障碍设置", () -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        add(column, "检查当前窗口与截图能力", () -> { ProbeAccessibilityService.requestScan(this); report.postDelayed(this::refresh, 1500); });
        add(column, "60 秒后运行一次唤醒检查", () -> {
            if (!((AlarmManager)getSystemService(ALARM_SERVICE)).canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            } else { scheduleProbe(this); refresh(); }
        });
        add(column, "60 秒系统闹钟接口对照", () -> {
            if (!((AlarmManager)getSystemService(ALARM_SERVICE)).canScheduleExactAlarms()) {
                startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            } else { scheduleProbe(this, true); refresh(); }
        });
        add(column, "授予本实验位置权限", () -> requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 1));
        add(column, "检查位置模拟能力", () -> { ProbeState.put(this, "locationInspection", json(LocationProbe.inspect(this))); refresh(); });
        add(column, "运行专用位置源实验", () -> new Thread(() -> {
            ProbeState.put(this, "locationFixture", json(LocationProbe.runMockFixture(this)));
            runOnUiThread(this::refresh);
        }).start());
        report = new TextView(this); report.setTextIsSelectable(true); column.addView(report);
        if (getIntent().getBooleanExtra("pin_setup", false)) startActivity(new Intent(this, PinSetupActivity.class));
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); setIntent(intent);
        if (intent.getBooleanExtra("pin_setup", false)) startActivity(new Intent(this, PinSetupActivity.class));
    }
    private void add(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(v -> action.run()); parent.addView(button);
    }
    private static org.json.JSONObject json(String value) {
        try { return new org.json.JSONObject(value); } catch (Exception ignored) { return new org.json.JSONObject(); }
    }
    private void refresh() { if (report != null) try { report.setText(ProbeState.status(this).toString(2)); } catch (Exception ignored) {} }
    private void scheduleTrial(boolean checkout) {
        try { OneShotRunner.schedule(this, checkout); refresh(); }
        catch (Exception ignored) { android.widget.Toast.makeText(this, "测试尚未安排，请检查闹钟权限或当前任务。", android.widget.Toast.LENGTH_LONG).show(); }
    }
    static void scheduleProbe(Context context) {
        scheduleProbe(context, false);
    }
    static void scheduleProbe(Context context, boolean alarmClock) {
        AlarmManager alarms = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (!alarms.canScheduleExactAlarms()) throw new IllegalStateException("exact-alarm-required");
        long at = System.currentTimeMillis() + 60000;
        long elapsedAt = SystemClock.elapsedRealtime() + 60000;
        String alarmId = UUID.randomUUID().toString();
        Intent alarm = new Intent(context, ProbeAlarmReceiver.class)
            .putExtra("probe_alarm_id", alarmId).putExtra("probe_scheduled_at", at).putExtra("probe_scheduled_elapsed", elapsedAt);
        PendingIntent target = PendingIntent.getBroadcast(context, 1, alarm, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (alarmClock) {
            PendingIntent show = PendingIntent.getActivity(context, 2, new Intent(context, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), target);
        } else {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, target);
        }
        ProbeState.putValues(context, "alarmId", alarmId, "alarmScheduledAt", at, "alarmScheduledElapsedRealtime", elapsedAt,
            "alarmMode", alarmClock ? "alarm-clock" : "exact-allow-while-idle",
            "alarmPlannedApi", alarmClock ? "AlarmManager.setAlarmClock" : "AlarmManager.setExactAndAllowWhileIdle");
    }
}

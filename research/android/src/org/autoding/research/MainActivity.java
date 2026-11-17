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
        detail.setText("独立实验：检查锁屏控件、截图和定时唤醒；位置测试使用专用合成源。此版本没有 PIN 存储或输入，不启动钉钉，不确认考勤。");
        column.addView(detail);
        add(column, "刷新能力报告", this::refresh);
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
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    private void add(LinearLayout parent, String label, Runnable action) {
        Button button = new Button(this); button.setText(label); button.setOnClickListener(v -> action.run()); parent.addView(button);
    }
    private static org.json.JSONObject json(String value) {
        try { return new org.json.JSONObject(value); } catch (Exception ignored) { return new org.json.JSONObject(); }
    }
    private void refresh() { if (report != null) try { report.setText(ProbeState.status(this).toString(2)); } catch (Exception ignored) {} }
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

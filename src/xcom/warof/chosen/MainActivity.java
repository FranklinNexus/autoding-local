/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private TextView state, events;
    private Button morningButton, eveningButton;
    private CheckBox weekdays;
    private Spinner jitter;
    private final int[] jitterValues = {0, 1, 3, 5, 10, 15};
    private int morning, evening;
    private boolean opening;
    private boolean pendingOpen;
    private final Handler handler = new Handler(android.os.Looper.getMainLooper());
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        Scheduler.log(this, "测试页已创建");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        morning = Scheduler.prefs(this).getInt("morning", 540);
        evening = Scheduler.prefs(this).getInt("evening", 1080);
        buildUi();
        acceptAlarm(getIntent());
        Scheduler.restore(this);
    }
    @Override protected void onResume() {
        super.onResume();
        Scheduler.log(this, "测试页已显示");
        refresh();
        if (pendingOpen) { pendingOpen = false; launchDing(); }
    }
    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i); setIntent(i);
        acceptAlarm(i);
    }
    private void acceptAlarm(Intent i) {
        if (!"xcom.warof.chosen.OPEN_DING".equals(i.getAction())) return;
        int kind = i.getIntExtra("kind", -1);
        long expected = i.getLongExtra("scheduled_at", 0);
        if ((kind != Scheduler.TEST && kind != Scheduler.MORNING && kind != Scheduler.EVENING) || expected == 0
            || expected != Scheduler.prefs(this).getLong("fired_" + kind, 0)) return;
        if (kind != Scheduler.TEST && !Scheduler.prefs(this).getBoolean("enabled", false)) return;
        Scheduler.prefs(this).edit().remove("fired_" + kind).commit();
        Scheduler.log(this, (kind == Scheduler.TEST ? "测试" : kind == Scheduler.MORNING ? "上班" : "下班") + "定时器已触发，页面已启动");
        pendingOpen = true;
        stopService(new Intent(this, AlarmService.class));
        ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).cancel(AlarmService.NOTICE);
        i.setAction(Intent.ACTION_MAIN);
    }
    private int dp(int n) { return (int)(getResources().getDisplayMetrics().density * n + .5f); }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this); col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(20), dp(30), dp(20), dp(28)); scroll.addView(col); setContentView(scroll);
        TextView title = text("AutoDing · 本地测试", 25); title.setTextColor(Color.rgb(25, 65, 105)); col.addView(title);
        col.addView(text("定时打开钉钉，由钉钉极速打卡处理。\n实际结果请查看钉钉考勤记录。", 15));
        state = text("", 15); state.setPadding(0, dp(14), 0, dp(10)); col.addView(state);
        button(col, "1. 允许精确定时", v -> {
            if (Build.VERSION.SDK_INT >= 31 && !Scheduler.exactAllowed(this)) startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + getPackageName())));
            else toast("精确定时权限已就绪");
        });
        button(col, "2. 允许后台显示（悬浮窗权限）", v -> startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))));
        button(col, "3. 查看华为后台运行设置", v -> new AlertDialog.Builder(this).setTitle("华为后台运行")
            .setMessage("在手机管家 → 应用启动管理中，找到本应用，关闭自动管理，开启自启动、关联启动和后台活动。\n\n最近任务中下拉本应用卡片加锁。测试熄屏唤醒时，请在手机上先完成解锁。")
            .setPositiveButton("打开应用设置", (d, which) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())))).setNegativeButton("关闭", null).show());
        button(col, "4. 允许定时启动通知", v -> {
            if (Build.VERSION.SDK_INT >= 26) startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        });
        button(col, "立即打开钉钉", v -> launchDing());
        button(col, "60 秒后测试一次", v -> {
            if (!checkPermissions()) return;
            try { long at = System.currentTimeMillis() + 60000; Scheduler.schedule(this, Scheduler.TEST, at); WatchService.start(this); Scheduler.log(this, "已安排一次测试：" + Scheduler.stamp(at)); refresh(); toast("可以回桌面等待测试；请保持充电亮屏"); }
            catch (RuntimeException e) { Scheduler.log(this, "安排测试出错：" + e.getClass().getSimpleName()); refresh(); }
        });
        button(col, "取消测试", v -> { Scheduler.cancel(this, Scheduler.TEST); if (!Scheduler.prefs(this).getBoolean("enabled", false)) stopService(new Intent(this, WatchService.class)); Scheduler.log(this, "已取消测试"); refresh(); });
        col.addView(text("日常定时", 20));
        morningButton = button(col, "", v -> new TimePickerDialog(this, (p, h, m) -> { morning = h * 60 + m; updateTimes(); }, morning / 60, morning % 60, true).show());
        eveningButton = button(col, "", v -> new TimePickerDialog(this, (p, h, m) -> { evening = h * 60 + m; updateTimes(); }, evening / 60, evening % 60, true).show());
        weekdays = new CheckBox(this); weekdays.setText("仅周一至周五（节假日、调休需手动调整）"); weekdays.setChecked(Scheduler.prefs(this).getBoolean("weekdays", true)); col.addView(weekdays);
        col.addView(text("浮动：上班提前、下班延后", 15));
        jitter = new Spinner(this);
        String[] choices = {"固定时间", "0–1 分钟", "0–3 分钟", "0–5 分钟", "0–10 分钟", "0–15 分钟"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, choices);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        jitter.setAdapter(adapter);
        int savedJitter = Scheduler.prefs(this).getInt("jitter", 5);
        for (int n = 0; n < jitterValues.length; n++) if (jitterValues[n] == savedJitter) jitter.setSelection(n);
        col.addView(jitter, new LinearLayout.LayoutParams(-1, -2));
        updateTimes();
        button(col, "保存并启用日常定时", v -> {
            if (!checkPermissions()) return;
            Scheduler.prefs(this).edit().putInt("morning", morning).putInt("evening", evening).putInt("jitter", jitterValues[jitter.getSelectedItemPosition()]).putBoolean("weekdays", weekdays.isChecked()).putBoolean("enabled", true).commit();
            try { Scheduler.scheduleDaily(this, Scheduler.MORNING); Scheduler.scheduleDaily(this, Scheduler.EVENING); WatchService.start(this); Scheduler.log(this, "已启用日常定时"); }
            catch (RuntimeException e) { Scheduler.prefs(this).edit().putBoolean("enabled", false).commit(); Scheduler.cancel(this, Scheduler.MORNING); Scheduler.cancel(this, Scheduler.EVENING); Scheduler.log(this, "启用失败，请检查权限"); }
            refresh();
        });
        button(col, "停用日常定时", v -> { Scheduler.prefs(this).edit().putBoolean("enabled", false).commit(); Scheduler.cancel(this, Scheduler.MORNING); Scheduler.cancel(this, Scheduler.EVENING); if (Scheduler.prefs(this).getLong("next_" + Scheduler.TEST, 0) == 0) stopService(new Intent(this, WatchService.class)); Scheduler.log(this, "已停用日常定时"); refresh(); });
        col.addView(text("本机运行记录", 20)); events = text("", 13); events.setTextIsSelectable(true); col.addView(events);
    }
    private TextView text(String s, int size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); t.setPadding(0, dp(6), 0, dp(6)); return t; }
    private Button button(LinearLayout col, String s, android.view.View.OnClickListener f) { Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(f); col.addView(b, new LinearLayout.LayoutParams(-1, -2)); return b; }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private void updateTimes() { morningButton.setText("上班：" + Scheduler.clock(morning)); eveningButton.setText("下班：" + Scheduler.clock(evening)); }
    private boolean checkPermissions() {
        if (!Scheduler.exactAllowed(this)) { toast("请先允许精确定时"); return false; }
        if (!Settings.canDrawOverlays(this)) { toast("请先允许后台显示"); return false; }
        if (Build.VERSION.SDK_INT >= 24 && !((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled()) { toast("请先允许定时启动通知"); return false; }
        return true;
    }
    private void refresh() {
        StringBuilder s = new StringBuilder("精确定时：").append(Scheduler.exactAllowed(this) ? "已允许" : "待设置").append("　后台显示：").append(Settings.canDrawOverlays(this) ? "已允许" : "待设置");
        s.append("\n通知：").append(Build.VERSION.SDK_INT < 24 || ((android.app.NotificationManager)getSystemService(NOTIFICATION_SERVICE)).areNotificationsEnabled() ? "已允许" : "待设置");
        long test = Scheduler.prefs(this).getLong("next_" + Scheduler.TEST, 0);
        if (test > 0) s.append("\n一次测试：").append(Scheduler.stamp(test));
        boolean enabled = Scheduler.prefs(this).getBoolean("enabled", false);
        s.append("\n日常定时：").append(enabled ? "已启用" : "停用");
        if (enabled) {
            s.append("（").append(Scheduler.prefs(this).getBoolean("weekdays", true) ? "周一至周五" : "每天").append("）");
            s.append("\n浮动窗口：0–").append(Scheduler.prefs(this).getInt("jitter", 5)).append(" 分钟");
            s.append("\n下次上班：").append(Scheduler.stamp(Scheduler.prefs(this).getLong("next_" + Scheduler.MORNING, 0)));
            s.append("\n下次下班：").append(Scheduler.stamp(Scheduler.prefs(this).getLong("next_" + Scheduler.EVENING, 0)));
        }
        state.setText(s); events.setText(Scheduler.prefs(this).getString("events", "暂无记录"));
    }
    private void launchDing() {
        if (opening) return;
        opening = true;
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true); }
        else getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        KeyguardManager km = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        if (km.isKeyguardLocked()) {
            if (km.isDeviceSecure()) { Scheduler.log(this, "手机处于密码锁定，请在手机上解锁后重试"); toast("请先在手机上解锁"); opening = false; refresh(); return; }
            if (Build.VERSION.SDK_INT >= 26) {
                km.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override public void onDismissSucceeded() { openAfterWake(); }
                    @Override public void onDismissError() { opening = false; Scheduler.log(MainActivity.this, "锁屏解除失败，请在手机上检查"); refresh(); }
                    @Override public void onDismissCancelled() { opening = false; }
                }); return;
            }
        }
        openAfterWake();
    }
    private void openAfterWake() {
        handler.postDelayed(() -> {
            try {
                Intent target = getPackageManager().getLaunchIntentForPackage("com.alibaba.android.rimet");
                if (target == null) { Scheduler.log(this, "未找到钉钉，请检查是否已安装"); return; }
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                Scheduler.log(this, "已发出打开钉钉请求；考勤结果待核实");
            } catch (RuntimeException e) { Scheduler.log(this, "打开钉钉出错：" + e.getClass().getSimpleName()); }
            finally { opening = false; refresh(); }
        }, 1200);
    }
}

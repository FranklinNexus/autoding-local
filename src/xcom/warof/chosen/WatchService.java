/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.TextView;

public final class WatchService extends Service {
    private WindowManager window;
    private TextView badge;
    private String status = "等待定时", lastState;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable pollStatus = new Runnable() {
        @Override public void run() {
            updateStatus(false);
            checkResultAttention();
            handler.postDelayed(this, 15000);
        }
    };
    private void checkResultAttention() {
        org.json.JSONObject state = Scheduler.status(this);
        String date = state.optString("workDate");
        android.content.SharedPreferences p = Scheduler.prefs(this);
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        boolean outstanding = false;
        for (int kind : new int[]{Scheduler.MORNING, Scheduler.EVENING}) {
            long attempted = p.getLong("attempted_" + date + "_" + kind, 0);
            if (p.getLong("actual_" + date + "_" + kind, 0) > 0 || attempted == 0 || System.currentTimeMillis() - attempted < Scheduler.LAUNCH_WINDOW_MS) continue;
            outstanding = true;
            String alert = "alerted_" + date + "_" + kind;
            if (p.getBoolean(alert, false)) continue;
            if (!"missed".equals(p.getString("stage_" + date + "_" + kind, ""))) p.edit().putString("stage_" + date + "_" + kind, "unverified").commit();
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("result_attention", "考勤结果待核对", NotificationManager.IMPORTANCE_HIGH));
            PendingIntent open = PendingIntent.getActivity(this, 703, new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "result_attention") : new Notification.Builder(this);
            nm.notify(403, builder.setSmallIcon(android.R.drawable.ic_dialog_alert).setContentTitle("AutoDing · 请核对考勤")
                .setContentText((kind == Scheduler.MORNING ? "上班" : "下班") + "任务尚无实际打卡回执，请核对钉钉记录")
                .setContentIntent(open).setAutoCancel(true).build());
            p.edit().putBoolean(alert, true).commit();
            Scheduler.log(this, "考勤结果待核对提醒：" + date + " kind=" + kind);
        }
        if (!outstanding) nm.cancel(403);
    }
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i, int flags, int id) {
        handler.removeCallbacks(pollStatus);
        if (!Scheduler.prefs(this).getBoolean("enabled", false) && Scheduler.prefs(this).getLong("next_" + Scheduler.TEST, 0) == 0
            && Scheduler.prefs(this).getLong("fired_" + Scheduler.TEST, 0) == 0) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(new NotificationChannel("local_wait", "定时守候状态", NotificationManager.IMPORTANCE_LOW));
        updateStatus(true);
        Intent show = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (badge == null && Settings.canDrawOverlays(this)) {
            window = (WindowManager)getSystemService(WINDOW_SERVICE);
            badge = new TextView(this);
            badge.setText("AutoDing · " + status); badge.setTextSize(13); badge.setTextColor(Color.WHITE);
            badge.setBackgroundColor(Color.argb(235, 30, 72, 110));
            int padding = (int)(10 * getResources().getDisplayMetrics().density);
            badge.setPadding(padding, padding / 2, padding, padding / 2);
            badge.setOnClickListener(v -> startActivity(show));
            int type = Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(-2, -2, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT);
            p.gravity = Gravity.BOTTOM | Gravity.START; p.x = padding; p.y = padding * 7;
            try { window.addView(badge, p); Scheduler.log(this, "已显示定时守候状态条"); }
            catch (RuntimeException e) { badge = null; Scheduler.log(this, "状态条显示失败，请检查后台显示权限"); }
        }
        handler.postDelayed(pollStatus, 15000);
        return START_STICKY;
    }
    private void updateStatus(boolean foreground) {
        boolean locked = ((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked();
        boolean interactive = ((PowerManager)getSystemService(POWER_SERVICE)).isInteractive();
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int plugged = battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        status = locked ? "手机已锁定 · 请解锁"
            : !interactive ? "屏幕已关闭 · 请检查"
            : plugged == 0 ? "未连接电源 · 请充电"
            : plugged < 0 ? "供电状态待确认 · 请检查" : "等待定时";
        if (!locked && interactive && plugged > 0 && !Scheduler.exactAllowed(this)) status = "精确定时权限待设置";
        else if (!locked && interactive && plugged > 0 && Scheduler.prefs(this).getBoolean("enabled", false)) {
            org.json.JSONObject attendance = Scheduler.status(this);
            if (attendance.optLong("checkOut", 0) > 0) status = "本班考勤已确认";
            else if (attendance.optLong("nextEvening", 0) > 0) status = "下班 " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA).format(new java.util.Date(attendance.optLong("nextEvening")));
            else if (attendance.optLong("checkIn", 0) == 0) status = "等待签到确认";
            else status = "下班待核实 · 请查看";
        }
        String state = locked + ":" + interactive + ":" + plugged + ":" + status;
        boolean changed = !state.equals(lastState);
        if (foreground || changed) {
            Intent show = new Intent(this, MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent open = PendingIntent.getActivity(this, 700, show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "local_wait") : new Notification.Builder(this);
            Notification notice = builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm).setContentTitle("AutoDing · " + status)
                .setContentText("点击查看下一次时间；停用日常定时可关闭状态条").setContentIntent(open).setOngoing(true).build();
            if (foreground) startForeground(402, notice);
            else ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(402, notice);
            if (badge != null) badge.setText("AutoDing · " + status);
        }
        if (changed) {
            lastState = state;
            Scheduler.log(this, "守候状态：" + status + "（锁定=" + locked + "，亮屏=" + interactive + "，供电=" + (plugged > 0) + "）");
        }
    }
    @Override public void onDestroy() {
        handler.removeCallbacks(pollStatus);
        if (badge != null && window != null) { try { window.removeView(badge); } catch (RuntimeException ignored) {} }
        stopForeground(true); super.onDestroy();
    }
    static void start(Context c) {
        Intent i = new Intent(c, WatchService.class);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }
}

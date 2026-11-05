/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.app.*;
import android.content.*;
import android.os.*;

public final class AlarmService extends Service {
    static final int NOTICE = 401;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        int kind = i.getIntExtra("kind", -1);
        long at = i.getLongExtra("scheduled_at", 0);
        KeyguardManager keyguard = (KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        PowerManager power = (PowerManager)getSystemService(POWER_SERVICE);
        boolean locked = keyguard.isKeyguardLocked();
        Scheduler.log(this, "触发时设备状态：锁屏=" + locked + "，密码保护=" + keyguard.isDeviceSecure() + "，亮屏=" + power.isInteractive());
        NotificationManager nm = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel("scheduled_launch", "定时启动提醒", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("显示已设置的定时启动任务");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            nm.createNotificationChannel(channel);
        }
        Intent open = new Intent(this, MainActivity.class).setAction("xcom.warof.chosen.OPEN_DING")
            .putExtra("kind", kind).putExtra("scheduled_at", at)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent target = PendingIntent.getActivity(this, kind + 500, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, "scheduled_launch") : new Notification.Builder(this);
        Notification notification = builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("AutoDing 定时启动")
            .setContentText(locked ? "手机已锁定，请在手机上解锁并检查考勤" : "启动钉钉的预定时间已到；点击查看")
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
            .setContentIntent(target).setFullScreenIntent(target, true).build();
        startForeground(NOTICE, notification);
        Scheduler.log(this, "已显示定时启动提醒");
        // The fullscreen notification handles sleeping devices; the explicit request
        // also supports an already-visible test activity.
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> {
            try {
                startActivity(open);
                Scheduler.log(this, "已请求启动页面，等待页面确认");
            } catch (RuntimeException e) { Scheduler.log(this, "启动页面请求出错：" + e.getClass().getSimpleName() + "，请点击提醒检查"); }
        }, 800);
        handler.postDelayed(() -> {
            if (Scheduler.prefs(this).getLong("fired_" + kind, 0) == at) {
                Scheduler.log(this, "触发后10秒仍未收到页面启动确认；当前锁屏=" + keyguard.isKeyguardLocked() + "，请检查系统启动限制及锁屏");
            }
        }, 10000);
        handler.postDelayed(() -> {
            if (Scheduler.prefs(this).getLong("fired_" + kind, 0) == at) {
                Scheduler.prefs(this).edit().remove("fired_" + kind).commit();
                Scheduler.log(this, "本次定时启动超时，已清除待处理请求；考勤待核实");
            }
            stopForeground(true); stopSelf();
        }, Scheduler.LAUNCH_WINDOW_MS);
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}

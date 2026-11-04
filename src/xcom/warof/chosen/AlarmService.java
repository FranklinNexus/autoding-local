/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.app.*;
import android.content.*;
import android.os.*;

public final class AlarmService extends Service {
    static final int NOTICE = 401;
    @Override public IBinder onBind(Intent i) { return null; }
    @Override public int onStartCommand(Intent i, int flags, int startId) {
        if (i == null) { stopSelf(); return START_NOT_STICKY; }
        int kind = i.getIntExtra("kind", -1);
        long at = i.getLongExtra("scheduled_at", 0);
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
            .setContentText("启动钉钉的预定时间已到；点击查看")
            .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC).setOngoing(true)
            .setContentIntent(target).setFullScreenIntent(target, true).build();
        startForeground(NOTICE, notification);
        Scheduler.log(this, "已显示定时启动提醒");
        // The fullscreen notification handles sleeping devices; the explicit request
        // also supports an already-visible test activity.
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { startActivity(open); } catch (RuntimeException e) { Scheduler.log(this, "请点击定时启动提醒继续"); }
        }, 800);
        new Handler(Looper.getMainLooper()).postDelayed(() -> { stopForeground(true); stopSelf(); }, 120000);
        return START_NOT_STICKY;
    }
}

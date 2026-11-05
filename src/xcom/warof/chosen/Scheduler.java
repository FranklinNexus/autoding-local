/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

final class Scheduler {
    static final int MORNING = 100, EVENING = 101, TEST = 200;
    static final long LAUNCH_WINDOW_MS = 120000;
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("local_schedule", Context.MODE_PRIVATE); }
    static AlarmManager manager(Context c) { return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
    static boolean exactAllowed(Context c) { return android.os.Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms(); }
    static String stamp(long t) { return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date(t)); }
    static String clock(int minutes) { return String.format(Locale.CHINA, "%02d:%02d", minutes / 60, minutes % 60); }
    static PendingIntent pending(Context c, int kind, long at) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction("xcom.warof.chosen.LOCAL_ALARM")
            .putExtra("kind", kind).putExtra("scheduled_at", at);
        return PendingIntent.getBroadcast(c, kind, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private static void cancelLegacy(Context c, int kind) {
        Intent old = new Intent(c, MainActivity.class).setAction("xcom.warof.chosen.OPEN_DING");
        PendingIntent p = PendingIntent.getActivity(c, kind, old, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (p != null) { manager(c).cancel(p); p.cancel(); }
    }
    static void schedule(Context c, int kind, long at) {
        cancelLegacy(c, kind);
        Intent show = new Intent(c, MainActivity.class).setAction(Intent.ACTION_MAIN);
        PendingIntent showApp = PendingIntent.getActivity(c, 300, show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(at, showApp), pending(c, kind, at));
        prefs(c).edit().putLong("next_" + kind, at).commit();
    }
    static void cancel(Context c, int kind) {
        cancelLegacy(c, kind);
        manager(c).cancel(pending(c, kind, 0));
        prefs(c).edit().remove("next_" + kind).remove("fired_" + kind).commit();
    }
    static void scheduleDaily(Context c, int kind) { scheduleDaily(c, kind, false); }
    static void scheduleDaily(Context c, int kind, boolean tomorrowOnly) {
        SharedPreferences p = prefs(c);
        int minutes = p.getInt(kind == MORNING ? "morning" : "evening", kind == MORNING ? 540 : 1080);
        long at = SchedulePlanner.next(System.currentTimeMillis(), TimeZone.getDefault(), minutes,
            kind == MORNING, p.getInt("jitter", 5), p.getBoolean("weekdays", true), tomorrowOnly, new Random());
        schedule(c, kind, at);
    }
    static void restore(Context c) { restore(c, false); }
    static void restore(Context c, boolean clockChanged) {
        if (prefs(c).getBoolean("enabled", false) && exactAllowed(c)) {
            try {
                for (int kind : new int[]{MORNING, EVENING}) {
                    long saved = prefs(c).getLong("next_" + kind, 0);
                    if (!clockChanged && saved > System.currentTimeMillis()) schedule(c, kind, saved);
                    else scheduleDaily(c, kind);
                }
                log(c, "已恢复上下班定时任务");
                WatchService.start(c);
            }
            catch (RuntimeException e) { log(c, "恢复任务出错：" + e.getClass().getSimpleName()); }
        }
        long test = prefs(c).getLong("next_" + TEST, 0);
        if (test > System.currentTimeMillis() && exactAllowed(c)) {
            try { schedule(c, TEST, test); } catch (RuntimeException e) { log(c, "测试任务恢复出错"); }
        } else if (test > 0) { cancel(c, TEST); log(c, "已清除过期测试任务"); }
    }
    static synchronized void log(Context c, String msg) {
        String lines = stamp(System.currentTimeMillis()) + "  " + msg + "\n" + prefs(c).getString("events", "");
        if (lines.length() > 6500) lines = lines.substring(0, 6500);
        prefs(c).edit().putString("events", lines).commit();
        android.util.Log.i("AutoDingLocal", msg);
    }
}

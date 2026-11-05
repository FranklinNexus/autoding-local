/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

final class Scheduler {
    static final int MORNING = 100, EVENING = 101, TEST = 200;
    static final long LAUNCH_WINDOW_MS = 120000;
    private static final Random RANDOM = new Random();
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("local_schedule", Context.MODE_PRIVATE); }
    static AlarmManager manager(Context c) { return (AlarmManager)c.getSystemService(Context.ALARM_SERVICE); }
    static boolean exactAllowed(Context c) { return android.os.Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms(); }
    static String stamp(long t) { return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(new Date(t)); }
    static String clock(int minutes) { return String.format(Locale.CHINA, "%02d:%02d", minutes / 60, minutes % 60); }
    private static String today() { return AttendancePlanner.dateKey(System.currentTimeMillis(), TimeZone.getDefault()); }
    private static String key(String prefix, String date, int kind) { return prefix + "_" + date + "_" + kind; }
    private static boolean dailyKind(int kind) { return kind == MORNING || kind == EVENING; }
    private static void require(boolean ok, String message) { if (!ok) throw new IllegalArgumentException(message); }
    private static void validateDate(String date) { AttendancePlanner.atLocalTime(date, 0, TimeZone.getDefault()); }
    private static TimeZone shiftZone(SharedPreferences p, String date) {
        return TimeZone.getTimeZone(p.getString("zone_" + date, TimeZone.getDefault().getID()));
    }
    private static int duration(SharedPreferences p, String date) { return p.getInt("duration_" + date, p.getInt("minimum_duration", 540)); }
    private static boolean workDay(SharedPreferences p, String date, TimeZone zone) {
        String override = p.getString("calendar." + date, "default");
        if ("work".equals(override)) return true;
        if ("rest".equals(override)) return false;
        Calendar cal = Calendar.getInstance(zone); cal.setTimeInMillis(AttendancePlanner.atLocalTime(date, 720, zone));
        int weekday = cal.get(Calendar.DAY_OF_WEEK);
        return !p.getBoolean("weekdays", true) || (weekday != Calendar.SATURDAY && weekday != Calendar.SUNDAY);
    }
    private static boolean eveningWorkDay(SharedPreferences p, String date) {
        // A real check-in starts a shift. Calendar edits may suppress a future
        // check-in, but must not erase the checkout of an already-started shift.
        return p.getLong(key("actual", date, MORNING), 0) > 0 || workDay(p, date, shiftZone(p, date));
    }
    private static boolean fixedSlotValid(SharedPreferences p, int kind, String date, long slot, TimeZone zone) {
        long base = AttendancePlanner.atLocalTime(date, p.getInt(kind == MORNING ? "morning" : "evening", kind == MORNING ? 540 : 1080), zone);
        long span = p.getInt("jitter", 5) * 60000L;
        long earliest = kind == MORNING ? Math.max(AttendancePlanner.atLocalTime(date, 0, zone), base - span) : base;
        long latest = kind == MORNING ? base : base + span;
        return slot >= earliest && slot <= latest;
    }
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
    static synchronized void schedule(Context c, int kind, long at) {
        require(dailyKind(kind) || kind == TEST, "Unknown schedule kind");
        require(at > System.currentTimeMillis(), "Schedule must be in the future");
        scheduleSlot(c, kind, at, AttendancePlanner.dateKey(at, TimeZone.getDefault()), TimeZone.getDefault().getID());
    }
    private static void scheduleSlot(Context c, int kind, long at, String date, String zone) {
        cancelLegacy(c, kind);
        Intent show = new Intent(c, MainActivity.class).setAction(Intent.ACTION_MAIN);
        PendingIntent showApp = PendingIntent.getActivity(c, 300, show, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager(c).setAlarmClock(new AlarmManager.AlarmClockInfo(at, showApp), pending(c, kind, at));
        SharedPreferences.Editor edit = prefs(c).edit().putLong("next_" + kind, at).putString("next_date_" + kind, date)
            .putString("next_zone_" + kind, zone);
        if (dailyKind(kind)) edit.putLong(key("slot", date, kind), at).putString(key("stage", date, kind), "scheduled");
        edit.commit();
    }
    private static void unschedule(Context c, int kind, boolean clearFired) {
        cancelLegacy(c, kind); manager(c).cancel(pending(c, kind, 0));
        SharedPreferences.Editor e = prefs(c).edit().remove("next_" + kind).remove("next_date_" + kind).remove("next_zone_" + kind);
        if (clearFired) e.remove("fired_" + kind).remove("fired_date_" + kind);
        e.commit();
    }
    static synchronized void cancel(Context c, int kind) { unschedule(c, kind, true); }

    static synchronized void applySettings(Context c, int morning, int evening, int minimumDuration, int jitter,
                                           boolean weekdays, boolean enabled) {
        require(morning >= 0 && morning < 1440 && evening >= 0 && evening < 1440, "Times must be 00:00..23:59");
        require(minimumDuration > 0 && minimumDuration <= 1440, "Duration must be 1..1440 minutes");
        require(jitter >= 0 && jitter <= 15, "Jitter must be 0..15 minutes");
        require(!enabled || exactAllowed(c), "Exact alarm permission is required before enabling");
        SharedPreferences p = prefs(c);
        boolean changed = p.getInt("morning", 540) != morning || p.getInt("evening", 1080) != evening
            || p.getInt("minimum_duration", 540) != minimumDuration || p.getInt("jitter", 5) != jitter
            || p.getBoolean("weekdays", true) != weekdays || p.getBoolean("enabled", false) != enabled;
        p.edit().putInt("morning", morning).putInt("evening", evening).putInt("minimum_duration", minimumDuration)
            .putInt("jitter", jitter).putBoolean("weekdays", weekdays).putBoolean("enabled", enabled)
            .putBoolean("dependent_evening", true).commit();
        if (enabled) restore(c);
        else {
            cancel(c, MORNING); cancel(c, EVENING);
            if (p.getLong("next_" + TEST, 0) == 0) c.stopService(new Intent(c, WatchService.class));
        }
        if (changed) log(c, enabled ? "已保存并启用日常定时；下班等待已确认上班记录" : "已保存设置并停用日常定时");
    }
    static synchronized void setDayOverride(Context c, String date, String mode) {
        validateDate(date);
        require("work".equals(mode) || "rest".equals(mode) || "default".equals(mode), "Calendar mode must be work/rest/default");
        SharedPreferences p = prefs(c);
        if (mode.equals(p.getString("calendar." + date, "default"))) return;
        SharedPreferences.Editor e = p.edit();
        if ("default".equals(mode)) e.remove("calendar." + date); else e.putString("calendar." + date, mode);
        e.commit(); log(c, "日期安排已更新：" + date + " " + mode); restore(c);
    }

    static synchronized String confirm(Context c, int kind, String workDate, long actual, String precision, String source) {
        require(dailyKind(kind), "Only morning/evening attendance can be confirmed");
        validateDate(workDate);
        require("minute".equals(precision) || "second".equals(precision), "Precision must be minute/second");
        require("user".equals(source) || "attendance-screen".equals(source), "Source must be user/attendance-screen");
        long now = System.currentTimeMillis();
        require(actual > 0 && actual <= now, "Attendance must be a past or current time");
        require(actual % ("minute".equals(precision) ? 60000 : 1000) == 0, "Attendance does not match its precision");
        SharedPreferences p = prefs(c); TimeZone zone = shiftZone(p, workDate);
        long start = AttendancePlanner.atLocalTime(workDate, 0, zone);
        Calendar end = Calendar.getInstance(zone); end.setTimeInMillis(start); end.add(Calendar.DAY_OF_MONTH, 2);
        if (kind == MORNING) require(workDate.equals(AttendancePlanner.dateKey(actual, zone)), "Check-in date differs from work date");
        else require(actual >= start && actual < end.getTimeInMillis(), "Check-out is outside the shift dates");
        long existing = p.getLong(key("actual", workDate, kind), 0);
        if (existing != 0) {
            require(existing == actual && precision.equals(p.getString(key("precision", workDate, kind), "")), "Conflicting attendance record");
            return "unchanged";
        }
        require(start <= now && now < end.getTimeInMillis(), "Work date is stale or in the future");
        if (kind == EVENING) {
            long checkIn = p.getLong(key("actual", workDate, MORNING), 0);
            require(checkIn > 0, "Check-in must be confirmed first");
            // The +60s conservative buffer is for planned launches, not a real minute-only receipt.
            long latestReported = actual + ("minute".equals(precision) ? 59999 : 0);
            require(latestReported >= checkIn + duration(p, workDate) * 60000L, "Check-out is earlier than the required duration");
        }
        SharedPreferences.Editor e = p.edit().putLong(key("actual", workDate, kind), actual)
            .putString(key("precision", workDate, kind), precision).putString(key("source", workDate, kind), source)
            .putString(key("stage", workDate, kind), "confirmed");
        if (kind == MORNING) e.putInt("duration_" + workDate, p.getInt("minimum_duration", 540))
            .putInt("evening_" + workDate, p.getInt("evening", 1080)).putInt("jitter_" + workDate, p.getInt("jitter", 5))
            .putString("zone_" + workDate, zone.getID());
        e.commit();
        if (workDate.equals(p.getString("next_date_" + kind, ""))) unschedule(c, kind, false);
        if (workDate.equals(p.getString("fired_date_" + kind, ""))) p.edit().remove("fired_" + kind).remove("fired_date_" + kind).commit();
        log(c, (kind == MORNING ? "已确认实际上班：" : "已确认实际下班：") + workDate + " " + stamp(actual) + "（" + precision + "，" + source + "）");
        if (p.getBoolean("enabled", false) && exactAllowed(c)) {
            scheduleDaily(c, MORNING); scheduleDaily(c, EVENING); WatchService.start(c);
        }
        return "confirmed";
    }

    private static String activeWorkDate(SharedPreferences p, long now) {
        String current = AttendancePlanner.dateKey(now, TimeZone.getDefault());
        Calendar previous = Calendar.getInstance(TimeZone.getDefault()); previous.setTimeInMillis(now); previous.add(Calendar.DAY_OF_MONTH, -1);
        String prior = AttendancePlanner.dateKey(previous.getTimeInMillis(), TimeZone.getDefault());
        for (String candidate : new String[]{p.getString("next_date_" + EVENING, ""), p.getString("fired_date_" + EVENING, ""), prior}) {
            if (candidate.isEmpty() || p.getLong(key("actual", candidate, MORNING), 0) == 0
                || p.getLong(key("actual", candidate, EVENING), 0) > 0) continue;
            AttendancePlanner.Plan old = eveningPlan(p, candidate, now, p.getLong(key("slot", candidate, EVENING), 0));
            if (old.ready || candidate.equals(p.getString("fired_date_" + EVENING, ""))) return candidate;
        }
        return current;
    }
    private static AttendancePlanner.Plan eveningPlan(SharedPreferences p, String date, long now, long saved) {
        return AttendancePlanner.plan(now, shiftZone(p, date), p.getInt("evening_" + date, p.getInt("evening", 1080)),
            p.getLong(key("actual", date, MORNING), 0), date, duration(p, date),
            "second".equals(p.getString(key("precision", date, MORNING), "minute")) ? AttendancePlanner.Precision.SECOND : AttendancePlanner.Precision.MINUTE,
            p.getInt("jitter_" + date, p.getInt("jitter", 5)), saved, RANDOM);
    }
    static void scheduleDaily(Context c, int kind) { scheduleDaily(c, kind, false); }
    static synchronized void scheduleDaily(Context c, int kind, boolean tomorrowOnly) {
        require(dailyKind(kind), "Daily kind required");
        SharedPreferences p = prefs(c); if (!p.getBoolean("enabled", false)) return;
        long now = System.currentTimeMillis();
        if (kind == EVENING && p.getBoolean("dependent_evening", true)) {
            String date = activeWorkDate(p, now);
            if (p.getLong(key("actual", date, EVENING), 0) > 0 || p.getLong(key("attempted", date, EVENING), 0) > 0) {
                unschedule(c, EVENING, false); return;
            }
            if (!eveningWorkDay(p, date)) {
                unschedule(c, EVENING, false); p.edit().putString(key("stage", date, EVENING), "rest").commit(); return;
            }
            long old = p.getLong(key("slot", date, EVENING), 0);
            AttendancePlanner.Plan plan = eveningPlan(p, date, now, old);
            if (!plan.ready) {
                unschedule(c, EVENING, false);
                p.edit().putString(key("stage", date, EVENING), "missing-check-in".equals(plan.reason) ? "waiting-confirmation" : "missed")
                    .putString("evening_reason_" + date, plan.reason).commit(); return;
            }
            if (old > 0 && old <= now) {
                unschedule(c, EVENING, false); p.edit().putString(key("stage", date, EVENING), "missed").commit(); return;
            }
            scheduleSlot(c, EVENING, plan.at, date, shiftZone(p, date).getID());
            p.edit().putString("evening_reason_" + date, "ready").commit(); return;
        }
        TimeZone zone = TimeZone.getDefault(); Calendar day = Calendar.getInstance(zone); day.setTimeInMillis(now);
        if (tomorrowOnly) day.add(Calendar.DAY_OF_MONTH, 1);
        for (int n = 0; n < 370; n++, day.add(Calendar.DAY_OF_MONTH, 1)) {
            String date = AttendancePlanner.dateKey(day.getTimeInMillis(), zone);
            if (!workDay(p, date, zone) || p.getLong(key("actual", date, kind), 0) > 0 || p.getLong(key("attempted", date, kind), 0) > 0) continue;
            int minutes = p.getInt(kind == MORNING ? "morning" : "evening", kind == MORNING ? 540 : 1080);
            long base = AttendancePlanner.atLocalTime(date, minutes, zone), span = p.getInt("jitter", 5) * 60000L;
            long earliest = kind == MORNING ? Math.max(AttendancePlanner.atLocalTime(date, 0, zone), base - span) : base;
            long latest = kind == MORNING ? base : base + span;
            long old = p.getLong(key("slot", date, kind), 0);
            if (old > 0 && old <= now) { p.edit().putString(key("stage", date, kind), "missed").commit(); continue; }
            if (latest <= now) { p.edit().putString(key("stage", date, kind), "missed").commit(); continue; }
            if (old > now && old >= earliest && old <= latest) { scheduleSlot(c, kind, old, date, zone.getID()); return; }
            long firstSecond = (Math.max(earliest, now + 1000) + 999) / 1000, lastSecond = latest / 1000;
            if (firstSecond > lastSecond) continue;
            long at = (firstSecond + RANDOM.nextInt((int)(lastSecond - firstSecond + 1))) * 1000;
            scheduleSlot(c, kind, at, date, zone.getID()); return;
        }
        unschedule(c, kind, false); log(c, "未找到可排程日期，请检查日期安排");
    }

    static synchronized boolean claim(Context c, int kind, long expected) {
        if ((!dailyKind(kind) && kind != TEST) || expected == 0) return false;
        SharedPreferences p = prefs(c); long now = System.currentTimeMillis();
        if (expected != p.getLong("next_" + kind, 0) || now < expected) return false;
        if (dailyKind(kind) && !p.getBoolean("enabled", false)) return false;
        String date = p.getString("next_date_" + kind, AttendancePlanner.dateKey(expected, TimeZone.getDefault()));
        if (dailyKind(kind) && (p.getLong(key("attempted", date, kind), 0) > 0 || p.getLong(key("actual", date, kind), 0) > 0)) return false;
        SharedPreferences.Editor e = p.edit().remove("next_" + kind).remove("next_date_" + kind).remove("next_zone_" + kind);
        if (dailyKind(kind)) e.putLong(key("attempted", date, kind), expected);
        boolean allowedDay = !dailyKind(kind) || (kind == EVENING ? eveningWorkDay(p, date) : workDay(p, date, shiftZone(p, date)));
        if (now - expected > LAUNCH_WINDOW_MS || !allowedDay) {
            if (dailyKind(kind)) e.putString(key("stage", date, kind), "missed");
            e.commit(); log(c, "过期或休息日定时已跳过：" + date + " kind=" + kind);
            if (dailyKind(kind)) scheduleDaily(c, kind, true);
            return false;
        }
        if (kind == EVENING && p.getBoolean("dependent_evening", true)) {
            AttendancePlanner.Plan plan = eveningPlan(p, date, expected - 1, expected);
            if (!AttendancePlanner.validSlot(expected, plan)) {
                e.putString(key("stage", date, kind), "missed").commit(); log(c, "下班任务约束校验未通过，已停止本次启动"); return false;
            }
        }
        e.putLong("fired_" + kind, expected).putString("fired_date_" + kind, date);
        if (dailyKind(kind)) e.putString(key("stage", date, kind), "attempted");
        e.commit(); return true;
    }
    static synchronized void markStage(Context c, int kind, String stage) {
        if (!dailyKind(kind)) return;
        require(stage != null && stage.matches("[a-z][a-z-]{0,40}"), "Invalid stage");
        SharedPreferences p = prefs(c); String date = p.getString("fired_date_" + kind, today());
        if (p.getLong(key("actual", date, kind), 0) == 0) p.edit().putString(key("stage", date, kind), stage).commit();
    }
    private static String pendingOutcome(Context c, int kind, long expected) {
        require(dailyKind(kind) || kind == TEST, "Unknown pending kind");
        SharedPreferences p = prefs(c); long now = System.currentTimeMillis();
        if (expected == 0 || p.getLong("fired_" + kind, 0) != expected) return "not-pending";
        if (dailyKind(kind) && !p.getBoolean("enabled", false)) return "disabled";
        String date = p.getString("fired_date_" + kind, "");
        if (dailyKind(kind) && p.getLong(key("actual", date, kind), 0) > 0) return "confirmed";
        if (dailyKind(kind) && (date.isEmpty() || p.getLong(key("attempted", date, kind), 0) != expected)) return "invalid-state";
        if (now < expected || now - expected > LAUNCH_WINDOW_MS) {
            expirePending(c, kind, expected); return "expired";
        }
        if (dailyKind(kind) && !(kind == EVENING ? eveningWorkDay(p, date) : workDay(p, date, shiftZone(p, date)))) return "rest";
        if (kind == EVENING && p.getBoolean("dependent_evening", true)
            && !AttendancePlanner.validSlot(expected, eveningPlan(p, date, expected - 1, expected))) return "invalid-constraint";
        return "pending";
    }
    static synchronized boolean acceptPendingPage(Context c, int kind, long expected) {
        if (!"pending".equals(pendingOutcome(c, kind, expected))) return false;
        // Showing our page is not a completed launch. Retain the exact token so
        // a locked device or failed activity request can recover within its window.
        markStage(c, kind, "page-opened"); return true;
    }
    static synchronized boolean pendingLaunchValid(Context c, int kind, long expected) {
        return "pending".equals(pendingOutcome(c, kind, expected));
    }
    static synchronized boolean acknowledgeLaunch(Context c, int kind, long expected) {
        SharedPreferences p = prefs(c);
        if (expected == 0 || p.getLong("fired_" + kind, 0) != expected) return false;
        markStage(c, kind, "launch-requested");
        p.edit().remove("fired_" + kind).remove("fired_date_" + kind).commit();
        return true;
    }
    static synchronized boolean expirePending(Context c, int kind, long expected) {
        SharedPreferences p = prefs(c); long now = System.currentTimeMillis();
        if (expected == 0 || p.getLong("fired_" + kind, 0) != expected
            || (now >= expected && now - expected <= LAUNCH_WINDOW_MS)) return false;
        markStage(c, kind, "missed");
        p.edit().remove("fired_" + kind).remove("fired_date_" + kind).commit();
        log(c, "本次定时启动超时，已清除待处理请求；考勤待核实"); return true;
    }
    static synchronized String resumePending(Context c, int kind, long expected) {
        String outcome = pendingOutcome(c, kind, expected);
        if (!"pending".equals(outcome)) return outcome;
        Intent service = new Intent(c, AlarmService.class).putExtra("kind", kind).putExtra("scheduled_at", expected);
        if (android.os.Build.VERSION.SDK_INT >= 26) c.startForegroundService(service); else c.startService(service);
        log(c, "恢复仍在有效期内的启动请求：kind=" + kind); return "resumed";
    }

    static void restore(Context c) { restore(c, false); }
    static synchronized void restore(Context c, boolean clockChanged) {
        SharedPreferences p = prefs(c); long now = System.currentTimeMillis();
        String zone = TimeZone.getDefault().getID(), oldZone = p.getString("last_zone", zone);
        if (!zone.equals(oldZone)) log(c, "系统时区已变更：" + oldZone + " → " + zone + "；已保存任务保留绝对时间，已确认班次沿用原时区约束，请核对");
        else if (clockChanged) log(c, "系统时间已变更；正在核对已保存绝对时间与考勤约束");
        p.edit().putString("last_zone", zone).commit();
        for (int kind : new int[]{MORNING, EVENING, TEST}) {
            long fired = p.getLong("fired_" + kind, 0);
            if (fired > 0 && (fired > now || now - fired > LAUNCH_WINDOW_MS)) {
                if (dailyKind(kind)) markStage(c, kind, "missed");
                p.edit().remove("fired_" + kind).remove("fired_date_" + kind).commit(); log(c, "过期启动请求已清理：kind=" + kind);
            }
        }
        if (p.getBoolean("enabled", false) && exactAllowed(c)) {
            try {
                for (int kind : new int[]{MORNING, EVENING}) {
                    long saved = p.getLong("next_" + kind, 0);
                    String date = p.getString("next_date_" + kind, saved > 0 ? AttendancePlanner.dateKey(saved, TimeZone.getDefault()) : "");
                    boolean valid = saved > now && !date.isEmpty() && (kind == EVENING ? eveningWorkDay(p, date) : workDay(p, date, shiftZone(p, date)))
                        && p.getLong(key("actual", date, kind), 0) == 0 && p.getLong(key("attempted", date, kind), 0) == 0;
                    if (valid && kind == EVENING && p.getBoolean("dependent_evening", true)) valid = AttendancePlanner.validSlot(saved, eveningPlan(p, date, now, saved));
                    else if (valid) valid = fixedSlotValid(p, kind, date, saved, TimeZone.getTimeZone(p.getString("next_zone_" + kind, zone)));
                    if (valid) scheduleSlot(c, kind, saved, date, p.getString("next_zone_" + kind, zone));
                    else {
                        if (saved > 0 && saved <= now && !date.isEmpty() && p.getLong(key("actual", date, kind), 0) == 0)
                            p.edit().putString(key("stage", date, kind), "missed").putLong(key("slot", date, kind), saved).commit();
                        scheduleDaily(c, kind);
                    }
                }
                WatchService.start(c);
            } catch (RuntimeException e) { log(c, "恢复任务出错：" + e.getClass().getSimpleName()); }
        }
        long test = p.getLong("next_" + TEST, 0);
        if (test > now && exactAllowed(c)) {
            try { scheduleSlot(c, TEST, test, p.getString("next_date_" + TEST, today()), zone); }
            catch (RuntimeException e) { log(c, "测试任务恢复出错"); }
        } else if (test > 0) { cancel(c, TEST); log(c, "已清除过期测试任务"); }
    }

    static synchronized JSONObject status(Context c) {
        SharedPreferences p = prefs(c); long now = System.currentTimeMillis();
        String date = activeWorkDate(p, now), current = today(); JSONObject state = new JSONObject();
        try {
            AttendancePlanner.Plan evening = eveningPlan(p, date, now, p.getLong(key("slot", date, EVENING), 0));
            state.put("todayDate", current).put("zone", TimeZone.getDefault().getID()).put("enabled", p.getBoolean("enabled", false))
                .put("nextMorning", p.getLong("next_" + MORNING, 0)).put("nextEvening", p.getLong("next_" + EVENING, 0))
                .put("workDate", date).put("checkIn", p.getLong(key("actual", date, MORNING), 0))
                .put("checkOut", p.getLong(key("actual", date, EVENING), 0)).put("earliestEvening", evening.earliest)
                .put("eveningReason", p.getLong(key("actual", date, EVENING), 0) > 0 ? "confirmed" : evening.reason)
                .put("stageMorning", p.getString(key("stage", date, MORNING), "waiting"))
                .put("stageEvening", p.getString(key("stage", date, EVENING), "waiting-confirmation"))
                .put("duration", duration(p, date)).put("jitter", p.getInt("jitter_" + date, p.getInt("jitter", 5)))
                .put("calendarToday", p.getString("calendar." + current, "default"))
                .put("checkInPrecision", p.getString(key("precision", date, MORNING), ""))
                .put("checkOutPrecision", p.getString(key("precision", date, EVENING), ""))
                .put("checkInSource", p.getString(key("source", date, MORNING), ""))
                .put("checkOutSource", p.getString(key("source", date, EVENING), ""));
            JSONArray pending = new JSONArray();
            for (int kind : new int[]{MORNING, EVENING, TEST}) {
                long at = p.getLong("fired_" + kind, 0);
                if (at > 0 && at <= now && now - at <= LAUNCH_WINDOW_MS)
                    pending.put(new JSONObject().put("kind", kind).put("at", at).put("workDate", p.getString("fired_date_" + kind, current)));
            }
            state.put("pending", pending);
        } catch (Exception e) { throw new IllegalStateException("Building schedule state failed", e); }
        return state;
    }

    static synchronized void log(Context c, String msg) {
        long now = System.currentTimeMillis();
        String lines = stamp(now) + "  " + msg + "\n" + prefs(c).getString("events", "");
        if (lines.length() > 6500) lines = lines.substring(0, 6500);
        prefs(c).edit().putString("events", lines).commit(); android.util.Log.i("AutoDingLocal", msg);
        try {
            File directory = c.getExternalFilesDir("logs");
            if (directory == null || (!directory.isDirectory() && !directory.mkdirs())) return;
            String date = today(), prior = prefs(c).getString("log_file_date", date);
            File file = new File(directory, "events.jsonl");
            if (!date.equals(prior) && file.isFile()) {
                File archive = new File(directory, "events-" + prior + ".jsonl");
                if (!archive.exists()) {
                    if (!file.renameTo(archive)) throw new java.io.IOException("Log rotation failed");
                } else {
                    // A clock/date reversal may revisit an already archived local day.
                    try (java.io.FileInputStream input = new java.io.FileInputStream(file);
                         FileOutputStream output = new FileOutputStream(archive, true)) {
                        byte[] buffer = new byte[4096]; int count;
                        while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    }
                    try (FileOutputStream output = new FileOutputStream(file, false)) { /* Reset the current-day file. */ }
                }
            }
            prefs(c).edit().putString("log_file_date", date).commit();
            JSONObject entry = new JSONObject().put("at", now).put("date", date).put("zone", TimeZone.getDefault().getID()).put("message", msg);
            try (FileOutputStream stream = new FileOutputStream(file, true)) { stream.write((entry.toString() + "\n").getBytes("UTF-8")); }
            Calendar cutoff = Calendar.getInstance(); cutoff.setTimeInMillis(now); cutoff.add(Calendar.DAY_OF_MONTH, -13);
            String oldest = AttendancePlanner.dateKey(cutoff.getTimeInMillis(), TimeZone.getDefault());
            File[] files = directory.listFiles();
            if (files != null) for (File old : files) {
                String name = old.getName();
                if (name.matches("events-[0-9]{4}-[0-9]{2}-[0-9]{2}\\.jsonl") && name.substring(7, 17).compareTo(oldest) < 0)
                    if (!old.delete()) android.util.Log.w("AutoDingLocal", "Old log could not be removed");
            }
        } catch (Exception e) { android.util.Log.w("AutoDingLocal", "File log failed: " + e.getClass().getSimpleName()); }
    }
}

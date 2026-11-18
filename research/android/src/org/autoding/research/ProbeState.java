package org.autoding.research;

import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;
import org.json.JSONObject;

final class ProbeState {
    static synchronized boolean putChecked(Context context, String key, Object value) {
        try {
            JSONObject report = saved(context);
            report.put(key, value); report.put("updatedAt", System.currentTimeMillis());
            return context.getSharedPreferences("probe", 0).edit().putString("report", report.toString()).commit();
        } catch (Exception ignored) { return false; }
    }
    static synchronized void put(Context context, String key, Object value) {
        putValues(context, key, value);
    }
    static synchronized void putValues(Context context, Object... pairs) {
        try {
            JSONObject report = saved(context);
            for (int n = 0; n + 1 < pairs.length; n += 2) report.put((String)pairs[n], pairs[n + 1]);
            save(context, report);
        } catch (Exception ignored) { /* No UI text or exception contents are persisted. */ }
    }
    static synchronized void beginScan(Context context, String scanId, long requestedAt, boolean accepted) {
        try {
            JSONObject report = saved(context);
            for (String key : new String[]{"windowScan", "screenshot", "scanError", "screenshotError"}) report.remove(key);
            report.put("scanId", scanId);
            report.put("scanRequestedAt", requestedAt);
            report.put("scanRequestedElapsedRealtime", SystemClock.elapsedRealtime());
            report.put("scanRequested", accepted);
            save(context, report);
        } catch (Exception ignored) {}
    }
    static synchronized boolean putScan(Context context, String scanId, String key, Object value) {
        try {
            JSONObject report = saved(context);
            // A screenshot callback may finish after a later scan has begun.
            // Keep each report scoped to the newest request.
            if (!scanId.equals(report.optString("scanId", ""))) return false;
            report.put(key, value);
            save(context, report);
            return true;
        } catch (Exception ignored) { return false; }
    }
    private static void save(Context context, JSONObject report) throws Exception {
        report.put("updatedAt", System.currentTimeMillis());
        context.getSharedPreferences("probe", 0).edit().putString("report", report.toString()).commit();
    }
    static JSONObject saved(Context context) {
        try { return new JSONObject(context.getSharedPreferences("probe", 0).getString("report", "{}")); }
        catch (Exception ignored) { return new JSONObject(); }
    }
    static JSONObject status(Context context) {
        JSONObject result = saved(context);
        try {
            KeyguardManager keyguard = (KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE);
            PowerManager power = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            result.put("api", Build.VERSION.SDK_INT);
            result.put("locked", keyguard.isKeyguardLocked());
            result.put("secure", keyguard.isDeviceSecure());
            result.put("interactive", power.isInteractive());
            result.put("powered", battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0);
            result.put("accessibilityConnected", ProbeAccessibilityService.active != null);
            result.put("exactAlarmAllowed", ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms());
            result.put("hasPinStorage", PinStore.isSaved(context));
            result.put("hasNetworkPermission", false);
            result.put("nativeSchedule", NativeTrialScheduler.status(context));
        } catch (Exception ignored) {}
        return result;
    }
}

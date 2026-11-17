package org.autoding.research;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.SystemClock;

public final class ProbeAlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String alarmId = intent == null ? "" : intent.getStringExtra("probe_alarm_id");
        if (alarmId == null || alarmId.isEmpty() || !alarmId.equals(ProbeState.saved(context).optString("alarmId", ""))) {
            ProbeState.put(context, "alarmIgnored", "stale-or-unidentified-probe");
            return;
        }
        ProbeState.putValues(context, "alarmFiredAt", System.currentTimeMillis(),
            "alarmFiredElapsedRealtime", SystemClock.elapsedRealtime(),
            "alarmFiredForId", alarmId,
            "alarmFiredForScheduledAt", intent.getLongExtra("probe_scheduled_at", 0),
            "alarmFiredForScheduledElapsedRealtime", intent.getLongExtra("probe_scheduled_elapsed", 0),
            "alarmLocked", ((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());
        PowerManager power = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        ProbeState.put(context, "alarmInteractiveBeforeWake", power.isInteractive());
        PowerManager.WakeLock wake = power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, "AutoDingResearch:probe");
        wake.acquire(8000);
        ProbeAccessibilityService.ScanRequest request = ProbeAccessibilityService.requestScan(context);
        ProbeState.putValues(context, "alarmScanRequested", request.accepted, "alarmScanId", request.id);
    }
}

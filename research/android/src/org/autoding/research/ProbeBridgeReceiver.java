package org.autoding.research;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Local ADB research control; no production attendance bridge. */
public final class ProbeBridgeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getStringExtra("operation");
        try {
            if ("scan".equals(action)) ProbeAccessibilityService.requestScan(context);
            else if ("alarm".equals(action)) MainActivity.scheduleProbe(context);
            else if ("alarmclock".equals(action)) MainActivity.scheduleProbe(context, true);
            else if ("trial-read".equals(action)) OneShotRunner.schedule(context, false, intent.getIntExtra("delay_seconds", 60));
            else if ("read-current".equals(action)) OneShotRunner.readCurrent(context);
            else if ("success-current".equals(action)) OneShotRunner.readSuccessCurrent(context);
            else if ("checkout-current".equals(action)) OneShotRunner.readCurrent(context, true);
            else if ("trial-checkout".equals(action)) OneShotRunner.schedule(context, true, intent.getIntExtra("delay_seconds", 60));
            else if ("trial-checkout-wall".equals(action)) OneShotRunner.schedule(context, true, intent.getIntExtra("delay_seconds", 180), true);
            else if ("trial-cancel".equals(action)) OneShotRunner.cancel(context);
            else if ("schedule-configure".equals(action)) NativeTrialScheduler.configure(context,
                intent.getStringExtra("start_date"), intent.getStringExtra("end_date"),
                intent.getIntExtra("morning_minutes", 420), intent.getIntExtra("floor_minutes", 1110),
                intent.getIntExtra("duration_minutes", 540), intent.getIntExtra("margin_minutes", 10),
                intent.getStringExtra("verified_date"), intent.getStringExtra("verified_checkin"));
            else if ("schedule-cancel".equals(action)) { NativeTrialScheduler.cancel(context); OneShotRunner.cancel(context); }
            else if ("schedule-restore".equals(action)) NativeTrialScheduler.restore(context);
            else if ("location".equals(action)) ProbeState.put(context, "locationFixture", new org.json.JSONObject(LocationProbe.runMockFixture(context)));
            else if ("stop".equals(action)) LocationProbe.stop(context);
            setResultCode(1); setResultData(ProbeState.status(context).toString());
        } catch (Exception ignored) { setResultCode(0); setResultData("{\"outcome\":\"probe-operation-failed\"}"); }
    }
}

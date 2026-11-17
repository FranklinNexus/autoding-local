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
            else if ("location".equals(action)) ProbeState.put(context, "locationFixture", new org.json.JSONObject(LocationProbe.runMockFixture(context)));
            else if ("stop".equals(action)) LocationProbe.stop(context);
            setResultCode(1); setResultData(ProbeState.status(context).toString());
        } catch (Exception ignored) { setResultCode(0); setResultData("{\"outcome\":\"probe-operation-failed\"}"); }
    }
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.json.JSONObject;

/** Only the system/ADB shell with DUMP permission may send companion receipts. */
public final class BridgeReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        try {
            String action = i.getAction(), outcome = "state";
            if ("xcom.warof.chosen.CONFIRM".equals(action)) {
                outcome = Scheduler.confirm(c, i.getIntExtra("kind", -1), i.getStringExtra("work_date"),
                    i.getLongExtra("actual_at", 0), i.getStringExtra("precision"), i.getStringExtra("source"));
            } else if ("xcom.warof.chosen.RESUME".equals(action)) {
                outcome = Scheduler.resumePending(c, i.getIntExtra("kind", -1), i.getLongExtra("scheduled_at", 0));
            } else if (!"xcom.warof.chosen.STATE".equals(action)) {
                throw new IllegalArgumentException("unknown-action");
            }
            JSONObject result = Scheduler.status(c);
            result.put("outcome", outcome);
            setResultCode(1); setResultData(result.toString());
        } catch (Exception e) {
            JSONObject result = new JSONObject();
            try { result.put("outcome", "rejected"); result.put("reason", e instanceof IllegalArgumentException ? e.getMessage() : "bridge-operation-failed"); }
            catch (Exception ignored) {}
            setResultCode(-1); setResultData(result.toString());
        }
    }
}

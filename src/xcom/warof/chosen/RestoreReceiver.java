/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class RestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (i == null) return;
        Scheduler.restore(c, Intent.ACTION_TIME_CHANGED.equals(i.getAction()) || Intent.ACTION_TIMEZONE_CHANGED.equals(i.getAction()));
    }
}

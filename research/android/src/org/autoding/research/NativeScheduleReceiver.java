package org.autoding.research;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class NativeScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if ("org.autoding.research.NATIVE_TRIAL".equals(intent.getAction())) NativeTrialScheduler.onAlarm(context,intent);
        else NativeTrialScheduler.restore(context);
    }
}

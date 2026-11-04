/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int kind = i.getIntExtra("kind", -1);
        if (kind != Scheduler.TEST && kind != Scheduler.MORNING && kind != Scheduler.EVENING) return;
        if (kind != Scheduler.TEST && !Scheduler.prefs(c).getBoolean("enabled", false)) return;
        long at = i.getLongExtra("scheduled_at", 0);
        if (at == 0 || Scheduler.prefs(c).getLong("next_" + kind, 0) != at) return;
        Scheduler.prefs(c).edit().remove("next_" + kind).putLong("fired_" + kind, at).commit();
        if (kind != Scheduler.TEST) {
            try { Scheduler.scheduleDaily(c, kind, true); }
            catch (RuntimeException e) { Scheduler.log(c, "下次排程失败，请检查定时权限"); }
        }
        Scheduler.log(c, "系统闹钟已触发，准备显示启动提醒");
        try {
            Intent service = new Intent(c, AlarmService.class).putExtra("kind", kind).putExtra("scheduled_at", at);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(service); else c.startService(service);
        } catch (RuntimeException e) { Scheduler.log(c, "启动提醒失败：" + e.getClass().getSimpleName()); }
    }
}

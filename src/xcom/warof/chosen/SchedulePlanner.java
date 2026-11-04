/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

final class SchedulePlanner {
    static long next(long now, TimeZone zone, int minutes, boolean morning,
                     int jitterMinutes, boolean weekdays, boolean tomorrowOnly, Random random) {
        Calendar day = Calendar.getInstance(zone);
        day.setTimeInMillis(now);
        if (tomorrowOnly) day.add(Calendar.DAY_OF_YEAR, 1);
        for (int n = 0; n < 8; n++, day.add(Calendar.DAY_OF_YEAR, 1)) {
            if (weekdays && (day.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY || day.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY)) continue;
            Calendar base = (Calendar)day.clone();
            base.set(Calendar.HOUR_OF_DAY, minutes / 60);
            base.set(Calendar.MINUTE, minutes % 60);
            base.set(Calendar.SECOND, 0);
            base.set(Calendar.MILLISECOND, 0);
            long window = Math.max(0, Math.min(60, jitterMinutes)) * 60000L;
            long earliest = base.getTimeInMillis() - (morning ? window : 0);
            long latest = base.getTimeInMillis() + (morning ? 0 : window);
            // Keep a selected slot in this day and strictly after the current time.
            Calendar floor = (Calendar)base.clone();
            floor.set(Calendar.HOUR_OF_DAY, 0);
            floor.set(Calendar.MINUTE, 0);
            earliest = Math.max(earliest, floor.getTimeInMillis());
            floor.add(Calendar.DAY_OF_YEAR, 1);
            latest = Math.min(latest, floor.getTimeInMillis() - 1000);
            earliest = Math.max(earliest, now + 1000);
            long firstSecond = (earliest + 999) / 1000;
            long lastSecond = latest / 1000;
            if (firstSecond <= lastSecond) return (firstSecond + random.nextInt((int)(lastSecond - firstSecond + 1))) * 1000;
        }
        throw new IllegalStateException("No upcoming workday");
    }
}

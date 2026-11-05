/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/** Computes one shift's evening window from a confirmed check-in, never from a planned start. */
final class AttendancePlanner {
    static final int DEFAULT_DURATION_MINUTES = 540;
    enum Precision { SECOND, MINUTE }

    static final class Plan {
        final boolean ready;
        final String reason, workDate;
        final long now, earliest, latest, at;
        private Plan(boolean ready, String reason, String workDate, long now, long earliest, long latest, long at) {
            this.ready = ready; this.reason = reason; this.workDate = workDate;
            this.now = now; this.earliest = earliest; this.latest = latest; this.at = at;
        }
    }

    static Plan plan(long now, TimeZone zone, int eveningMinutes, long checkIn,
                     int durationMinutes, Precision precision, int jitterMinutes, Random random) {
        return plan(now, zone, eveningMinutes, checkIn, durationMinutes, precision, jitterMinutes, 0, random);
    }

    /** A still-future saved slot in the same window is retained without drawing randomness. */
    static Plan plan(long now, TimeZone zone, int eveningMinutes, long checkIn,
                     int durationMinutes, Precision precision, int jitterMinutes, long savedSlot, Random random) {
        return plan(now, zone, eveningMinutes, checkIn, checkIn > 0 ? dateKey(checkIn, zone) : null,
            durationMinutes, precision, jitterMinutes, savedSlot, random);
    }

    /** workDate identifies the local date on the confirmed attendance record (yyyy-MM-dd). */
    static Plan plan(long now, TimeZone zone, int eveningMinutes, long checkIn, String workDate,
                     int durationMinutes, Precision precision, int jitterMinutes, long savedSlot, Random random) {
        require(zone != null, "Time zone is required");
        require(now >= 0, "Current time must be nonnegative");
        require(eveningMinutes >= 0 && eveningMinutes < 1440, "Evening minutes must be 0..1439");
        require(durationMinutes > 0 && durationMinutes <= 1440, "Duration must be 1..1440 minutes");
        require(precision != null, "Precision is required");
        require(jitterMinutes >= 0 && jitterMinutes <= 15, "Jitter must be 0..15 minutes");
        require(random != null, "Random source is required");
        if (checkIn <= 0) return pending("missing-check-in", workDate, now, 0, 0);
        if (checkIn > now) return pending("future-check-in", workDate, now, 0, 0);
        String actualDate = dateKey(checkIn, zone);
        if (workDate == null || !actualDate.equals(workDate)) return pending("date-mismatch", workDate, now, 0, 0);
        Calendar day;
        try { day = localDay(workDate, zone); }
        catch (IllegalArgumentException e) { return pending("invalid-work-date", workDate, now, 0, 0); }
        if (precision == Precision.MINUTE && checkIn % 60000 != 0)
            return pending("invalid-minute-precision", workDate, now, 0, 0);

        long configured = atLocalTime(workDate, eveningMinutes, zone);
        long duration = durationMinutes * 60000L + (precision == Precision.MINUTE ? 60000L : 0);
        if (checkIn > Long.MAX_VALUE - duration) return pending("invalid-check-in", workDate, now, 0, 0);
        long earliest = Math.max(configured, checkIn + duration);
        Calendar following = (Calendar)day.clone(); following.add(Calendar.DAY_OF_MONTH, 1);
        long nextDay = following.getTimeInMillis();
        following.add(Calendar.DAY_OF_MONTH, 1);
        long endLimit = following.getTimeInMillis() - 1;
        if (earliest > endLimit) return pending("shift-too-long", workDate, now, earliest, endLimit);
        long latest = Math.min(earliest + jitterMinutes * 60000L, endLimit);

        String currentDate = dateKey(now, zone);
        // Yesterday's record belongs only to an actual overnight shift whose window is still open.
        if (!currentDate.equals(workDate)
            && !(currentDate.equals(dateKey(nextDay, zone)) && earliest >= nextDay))
            return pending("stale-check-in", workDate, now, earliest, latest);
        if (latest <= now) return pending("window-expired", workDate, now, earliest, latest);
        Plan window = new Plan(true, "ready", workDate, now, earliest, latest, 0);
        if (validSlot(savedSlot, window)) return new Plan(true, "ready", workDate, now, earliest, latest, savedSlot);
        // Keep the confirmed second/millisecond alignment, selecting whole-second offsets after the anchor.
        long first = now < earliest ? 0 : (now - earliest) / 1000 + 1;
        long last = (latest - earliest) / 1000;
        if (first > last) return pending("window-expired", workDate, now, earliest, latest);
        long at = earliest + (first + random.nextInt((int)(last - first + 1))) * 1000;
        return new Plan(true, "ready", workDate, now, earliest, latest, at);
    }

    static boolean validSlot(long slot, Plan window) {
        return window != null && window.ready && slot > window.now && slot >= window.earliest && slot <= window.latest;
    }

    static String dateKey(long epoch, TimeZone zone) {
        require(zone != null, "Time zone is required");
        require(epoch >= 0, "Time must be nonnegative");
        Calendar c = Calendar.getInstance(zone); c.setTimeInMillis(epoch);
        return String.format(Locale.ROOT, "%04d-%02d-%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static long atLocalTime(String workDate, int minutes, TimeZone zone) {
        require(minutes >= 0 && minutes < 1440, "Local minutes must be 0..1439");
        Calendar c = localDay(workDate, zone);
        c.set(Calendar.HOUR_OF_DAY, minutes / 60); c.set(Calendar.MINUTE, minutes % 60);
        return c.getTimeInMillis();
    }

    private static Calendar localDay(String workDate, TimeZone zone) {
        require(zone != null, "Time zone is required");
        require(workDate != null && workDate.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"), "Date must be yyyy-MM-dd");
        int year = Integer.parseInt(workDate.substring(0, 4));
        require(year >= 1970 && year <= 9999, "Year must be 1970..9999");
        Calendar c = new GregorianCalendar(zone, Locale.ROOT); c.clear(); c.setLenient(false);
        c.set(year, Integer.parseInt(workDate.substring(5, 7)) - 1, Integer.parseInt(workDate.substring(8, 10)), 0, 0, 0);
        c.getTimeInMillis();
        return c;
    }

    private static Plan pending(String reason, String workDate, long now, long earliest, long latest) {
        return new Plan(false, reason, workDate, now, earliest, latest, 0);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}

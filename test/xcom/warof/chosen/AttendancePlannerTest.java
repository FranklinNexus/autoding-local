/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import java.util.Calendar;
import java.util.Random;
import java.util.TimeZone;

public final class AttendancePlannerTest {
    static final TimeZone ZONE = TimeZone.getTimeZone("Asia/Shanghai");
    static int checks;
    static long at(String date, int hour, int minute, int second, TimeZone zone) {
        return AttendancePlanner.atLocalTime(date, hour * 60 + minute, zone) + second * 1000L;
    }
    static long at(String date, int hour, int minute, int second) { return at(date, hour, minute, second, ZONE); }
    static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    static void bad(Runnable action, String message) {
        boolean rejected = false; try { action.run(); } catch (IllegalArgumentException e) { rejected = true; }
        check(rejected, message);
    }
    static AttendancePlanner.Plan plan(long now, long checkIn, AttendancePlanner.Precision precision, int jitter, Random random) {
        return AttendancePlanner.plan(now, ZONE, 1080, checkIn, 540, precision, jitter, random);
    }
    public static void main(String[] args) {
        long now = at("2026-11-05", 10, 0, 0), checkIn = at("2026-11-05", 9, 18, 0);
        AttendancePlanner.Plan minute = plan(now, checkIn, AttendancePlanner.Precision.MINUTE, 0, new Random(1));
        check(minute.ready && minute.at == at("2026-11-05", 18, 19, 0), "minute-only 09:18 needs >=18:19");
        AttendancePlanner.Plan second = plan(now, checkIn + 37000, AttendancePlanner.Precision.SECOND, 0, new Random(1));
        check(second.ready && second.at == at("2026-11-05", 18, 18, 37), "known seconds preserve 09:18:37 +9h");
        check(plan(now, at("2026-11-05", 8, 0, 0), AttendancePlanner.Precision.SECOND, 0, new Random()).at
            == at("2026-11-05", 18, 0, 0), "early check-in still waits for configured evening");
        check(AttendancePlanner.plan(now, ZONE, 1140, checkIn, 540, AttendancePlanner.Precision.SECOND, 0, new Random()).at
            == at("2026-11-05", 19, 0, 0), "later configured evening respected");
        check(!plan(now, 0, AttendancePlanner.Precision.MINUTE, 5, new Random()).ready, "no record stays pending");
        check(!plan(now, now + 1, AttendancePlanner.Precision.SECOND, 5, new Random()).ready, "future record pending");
        check(!plan(now, at("2026-11-04", 9, 18, 0), AttendancePlanner.Precision.MINUTE, 5, new Random()).ready, "previous day record rejected");
        check(!AttendancePlanner.plan(now, ZONE, 1080, checkIn, "2026-11-04", 540,
            AttendancePlanner.Precision.MINUTE, 5, 0, new Random()).ready, "record date mismatch rejected");
        check(!plan(now, checkIn + 1, AttendancePlanner.Precision.MINUTE, 5, new Random()).ready, "minute record must be normalized");
        check(!plan(at("2026-11-05", 18, 24, 1), checkIn, AttendancePlanner.Precision.MINUTE, 5, new Random()).ready,
            "expired window stays pending rather than scheduling another day");
        long overnightIn = at("2026-11-05", 23, 30, 0);
        AttendancePlanner.Plan overnight = plan(at("2026-11-06", 1, 0, 0), overnightIn, AttendancePlanner.Precision.MINUTE, 0, new Random());
        check(overnight.ready && overnight.at == at("2026-11-06", 8, 31, 0), "same shift finishes after midnight");
        check(!plan(at("2026-11-07", 1, 0, 0), overnightIn, AttendancePlanner.Precision.MINUTE, 15, new Random()).ready,
            "overnight record never carries into later day");
        long saturday = at("2026-11-07", 10, 0, 0);
        check(plan(saturday, at("2026-11-07", 9, 18, 0), AttendancePlanner.Precision.MINUTE, 0, new Random()).ready,
            "confirmed weekend shift retained");
        TimeZone la = TimeZone.getTimeZone("America/Los_Angeles");
        long laCheckIn = at("2026-11-05", 9, 18, 37, la), laNow = at("2026-11-05", 10, 0, 0, la);
        check(AttendancePlanner.plan(laNow, la, 1080, laCheckIn, 540, AttendancePlanner.Precision.SECOND, 0, new Random()).at
            == at("2026-11-05", 18, 18, 37, la), "timezone local clock");
        check(AttendancePlanner.dateKey(at("2026-11-05", 0, 30, 0), la).equals("2026-11-04"), "date key honors zone boundary");
        // A DST-changing overnight shift still uses elapsed nine hours, not nine wall-clock hours.
        long dstIn = at("2026-11-01", 0, 30, 0, la);
        AttendancePlanner.Plan dst = AttendancePlanner.plan(at("2026-11-01", 1, 0, 0, la), la, 0, dstIn, 540,
            AttendancePlanner.Precision.SECOND, 0, new Random());
        check(dst.ready && dst.at == dstIn + 540 * 60000L, "duration uses elapsed time over DST");
        Random noDraw = new Random() { @Override public int nextInt(int bound) { throw new AssertionError("saved slot resampled"); } };
        long saved = at("2026-11-05", 18, 21, 12);
        AttendancePlanner.Plan kept = AttendancePlanner.plan(now, ZONE, 1080, checkIn, 540,
            AttendancePlanner.Precision.MINUTE, 5, saved, noDraw);
        check(kept.at == saved && AttendancePlanner.validSlot(saved, kept), "valid saved slot retained");
        check(!AttendancePlanner.validSlot(kept.earliest - 1, kept), "saved before anchor invalid");
        check(!AttendancePlanner.validSlot(kept.latest + 1, kept), "saved after window invalid");
        check(!AttendancePlanner.validSlot(now, kept), "saved must be future");
        for (int seed = 0; seed < 1000; seed++) {
            AttendancePlanner.Plan p = plan(now, checkIn, AttendancePlanner.Precision.MINUTE, 15, new Random(seed));
            check(p.ready && p.at >= at("2026-11-05", 18, 19, 0) && p.at <= at("2026-11-05", 18, 34, 0), "jitter after anchor");
            AttendancePlanner.Plan remaining = plan(at("2026-11-05", 18, 22, 20), checkIn, AttendancePlanner.Precision.MINUTE, 5, new Random(seed));
            check(remaining.ready && remaining.at > at("2026-11-05", 18, 22, 20) && remaining.at <= at("2026-11-05", 18, 24, 0), "remaining window only");
        }
        Random first = new Random() { @Override public int nextInt(int bound) { return 0; } };
        Random last = new Random() { @Override public int nextInt(int bound) { return bound - 1; } };
        check(plan(now, checkIn, AttendancePlanner.Precision.MINUTE, 5, first).at == at("2026-11-05", 18, 19, 0), "jitter lower endpoint");
        check(plan(now, checkIn, AttendancePlanner.Precision.MINUTE, 5, last).at == at("2026-11-05", 18, 24, 0), "jitter upper endpoint");
        bad(() -> AttendancePlanner.atLocalTime("2026-02-30", 540, ZONE), "invalid calendar date");
        bad(() -> AttendancePlanner.atLocalTime("2026-2-03", 540, ZONE), "strict date format");
        bad(() -> AttendancePlanner.atLocalTime("2026-11-05", 1440, ZONE), "local minute bounds");
        bad(() -> AttendancePlanner.plan(now, null, 1080, checkIn, 540, AttendancePlanner.Precision.MINUTE, 5, new Random()), "null zone");
        bad(() -> plan(now, checkIn, AttendancePlanner.Precision.MINUTE, 16, new Random()), "jitter bounds");
        bad(() -> AttendancePlanner.plan(now, ZONE, 1080, checkIn, 0, AttendancePlanner.Precision.MINUTE, 5, new Random()), "duration bounds");
        bad(() -> plan(now, checkIn, null, 5, new Random()), "precision required");
        bad(() -> plan(now, checkIn, AttendancePlanner.Precision.MINUTE, 5, null), "random required");
        bad(() -> AttendancePlanner.plan(now, ZONE, -1, checkIn, 540, AttendancePlanner.Precision.MINUTE, 5, new Random()), "evening bounds");
        System.out.println("PASS: " + checks + " attendance checks (confirmed record, duration, precision, overnight, timezone, saved slots)");
    }
}

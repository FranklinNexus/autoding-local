/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

import android.content.Context;
import java.util.Random;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** In-memory rehearsal: no ADB, UI interaction, DingTalk, or attendance writes. */
public final class FullDryRunHarness {
    private static int checks;
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static Context enabled(String day) {
        Context c = new Context();
        c.prefs.edit().putString("calendar." + day, "work").commit();
        Scheduler.applySettings(c, 540, 1080, 540, 5, true, true);
        return c;
    }
    public static void main(String[] args) {
        if (args.length != 2) throw new IllegalArgumentException("Expected two synthetic OCR epochs");
        long observedIn = Long.parseLong(args[0]);
        long observedOut = Long.parseLong(args[1]);
        TimeZone shanghai = TimeZone.getTimeZone("Asia/Shanghai");
        TimeZone.setDefault(shanghai);
        String knownDay = AttendancePlanner.dateKey(observedIn, shanghai);
        long saved = AttendancePlanner.atLocalTime(knownDay, 18 * 60 + 22, shanghai) + 8000;
        long simulatedNow = AttendancePlanner.atLocalTime(knownDay, 17 * 60 + 50, shanghai);
        Random noRedraw = new Random() {
            @Override public int nextInt(int bound) { throw new AssertionError("Saved slot was redrawn"); }
        };
        AttendancePlanner.Plan exact = AttendancePlanner.plan(simulatedNow, shanghai, 1080,
            observedIn, knownDay, 540, AttendancePlanner.Precision.MINUTE, 5, saved, noRedraw);
        check(exact.ready && exact.earliest == AttendancePlanner.atLocalTime(knownDay, 18 * 60 + 19, shanghai),
            "09:18 minute receipt gives 18:19 earliest");
        check(exact.at == saved && AttendancePlanner.validSlot(saved, exact),
            "18:22:08 saved slot stays valid without redrawing");
        check(observedOut >= observedIn + 540 * 60000L && observedOut >= exact.earliest,
            "synthetic 18:22 checkout is after the nine-hour shift");

        // The test script compiles a disposable Scheduler copy whose clock reads
        // FixtureClock. Every other Scheduler statement is the production source.
        FixtureClock.set(simulatedNow);
        Context c = enabled(knownDay);
        check(c.prefs.getLong("next_101", 0) == 0, "checkout waits for actual check-in");
        c.prefs.edit().putLong("slot_" + knownDay + "_101", saved).commit();
        check("confirmed".equals(Scheduler.confirm(c, 100, knownDay, observedIn, "minute", "attendance-screen")),
            "OCR check-in passes through the real Scheduler");
        long planned = c.prefs.getLong("next_101", 0);
        check(planned == saved && planned > FixtureClock.now(), "saved 18:22:08 alarm is registered");
        check("unchanged".equals(Scheduler.confirm(c, 100, knownDay, observedIn, "minute", "attendance-screen"))
            && c.prefs.getLong("next_101", 0) == planned, "duplicate OCR receipt keeps the slot");
        Scheduler.applySettings(c, 540, 1080, 600, 1, true, true);
        check(c.prefs.getInt("duration_" + knownDay, 0) == 540
            && c.prefs.getLong("next_101", 0) == planned, "same shift retains frozen rules and slot");

        FixtureClock.set(saved + 1000);
        long token = saved;
        check(Scheduler.claim(c, 101, token), "one checkout alarm claim succeeds");
        check(!Scheduler.claim(c, 101, token), "same alarm cannot claim twice");
        check(Scheduler.acceptPendingPage(c, 101, token), "launch page opens within 120 seconds");
        check(c.prefs.getLong("fired_101", 0) == token, "locked page retains the recoverable token");
        int serviceStartsBeforeResume = c.serviceStarts;
        check("resumed".equals(Scheduler.resumePending(c, 101, token))
            && c.serviceStarts == serviceStartsBeforeResume + 1,
            "companion resumes the same pending request");
        check(Scheduler.pendingLaunchValid(c, 101, token), "launch still validates before opening DingTalk");
        check(Scheduler.acknowledgeLaunch(c, 101, token), "launch request consumes only its matching token");
        check("not-pending".equals(Scheduler.resumePending(c, 101, token)),
            "consumed request cannot relaunch after recovery");
        check("launch-requested".equals(c.prefs.getString("stage_" + knownDay + "_101", "")),
            "opening DingTalk is not a confirmed checkout");
        FixtureClock.set(AttendancePlanner.atLocalTime(knownDay, 18 * 60 + 25, shanghai));
        check("confirmed".equals(Scheduler.confirm(c, 101, knownDay, observedOut, "minute", "attendance-screen")),
            "OCR checkout receipt confirms the shift");
        check("unchanged".equals(Scheduler.confirm(c, 101, knownDay, observedOut, "minute", "attendance-screen")),
            "duplicate checkout receipt is unchanged");
        JSONObject status = Scheduler.status(c);
        check(((Number)status.get("checkOut")).longValue() == observedOut
            && "confirmed".equals(status.get("stageEvening")), "final state holds actual checkout and confirmation");
        check(((JSONArray)status.get("pending")).data.isEmpty()
            && c.prefs.getLong("next_101", 0) == 0, "no pending or duplicate evening alarm remains");

        Context expired = enabled(knownDay);
        Scheduler.confirm(expired, 100, knownDay, observedIn, "minute", "attendance-screen");
        long old = FixtureClock.now() - Scheduler.LAUNCH_WINDOW_MS - 1000;
        expired.prefs.edit().putLong("fired_101", old).putString("fired_date_101", knownDay)
            .putLong("attempted_" + knownDay + "_101", old).commit();
        check(!Scheduler.pendingLaunchValid(expired, 101, old), "expired checkout token does not open DingTalk");
        check(!"resumed".equals(Scheduler.resumePending(expired, 101, old)),
            "expired request cannot be recovered");
        System.out.println("PASS: " + checks + " isolated full-shift dry-run checks");
    }
}

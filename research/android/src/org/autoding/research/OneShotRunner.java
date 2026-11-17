package org.autoding.research;

import android.accessibilityservice.AccessibilityService;
import android.app.AlarmManager;
import android.app.KeyguardManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.json.JSONObject;

/** Explicit single experiment. No recurring schedule or production-state write. */
public final class OneShotRunner {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static OneShotRunner active;
    private final Context context;
    private final String id;
    private final boolean checkout;
    private final JSONObject evidence;
    private final long deadline = SystemClock.elapsedRealtime() + 150000;
    private PowerManager.WakeLock wake;
    private boolean finished;
    private long clickedAt;
    private String previousCheckout;
    private String expectedDate, expectedCheckIn;
    private int frameWindowId;
    private long frameLayoutVersion, frameContentVersion, frameRequestedElapsed;

    private OneShotRunner(Context context, String id, boolean checkout, JSONObject evidence) {
        this.context = context.getApplicationContext(); this.id = id;
        this.checkout = checkout; this.evidence = evidence;
    }

    public static synchronized String schedule(Context context, boolean checkout) throws Exception {
        return schedule(context, checkout, 60);
    }
    public static synchronized String schedule(Context context, boolean checkout, int delaySeconds) throws Exception {
        if (active != null) throw new IllegalStateException("trial-already-running");
        if (delaySeconds < 60 || delaySeconds > 600) throw new IllegalArgumentException("trial-delay-out-of-range");
        AlarmManager alarms = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        if (!alarms.canScheduleExactAlarms()) throw new IllegalStateException("exact-alarm-required");
        String id = UUID.randomUUID().toString();
        long at = System.currentTimeMillis() + delaySeconds * 1000L;
        JSONObject report = new JSONObject();
        report.put("id", id); report.put("status", "scheduled");
        report.put("checkoutEnabled", checkout); report.put("scheduledAt", at);
        report.put("scheduledElapsed", SystemClock.elapsedRealtime() + delaySeconds * 1000L);
        report.put("computerExecutionRequired", false);
        report.put("writesProductionState", false);
        Intent intent = new Intent(context, ProbeAlarmReceiver.class).putExtra("trial_id", id);
        PendingIntent target = PendingIntent.getBroadcast(context, 101, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent show = PendingIntent.getActivity(context, 102, new Intent(context, MainActivity.class),
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (!ProbeState.putChecked(context, "trial", report)) throw new IllegalStateException("trial-save-failed");
        alarms.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), target);
        return id;
    }

    public static synchronized void readCurrent(Context context) throws Exception {
        readCurrent(context, false);
    }
    public static synchronized void readCurrent(Context context, boolean checkout) throws Exception {
        if (active != null) throw new IllegalStateException("trial-already-running");
        JSONObject report = new JSONObject();
        String id = UUID.randomUUID().toString();
        report.put("id", id); report.put("status", "running");
        report.put("checkoutEnabled", checkout); report.put("currentPageOnly", true);
        report.put("computerExecutionRequired", false); report.put("writesProductionState", false);
        if (!ProbeState.putChecked(context, "trial", report)) throw new IllegalStateException("trial-save-failed");
        OneShotRunner run = new OneShotRunner(context, id, checkout, report);
        active = run;
        MAIN.postDelayed(() -> { if (!run.finished) run.finish("trial-timeout"); }, 150000);
        run.capture(false, 0);
    }

    /** Diagnostic only: inspect an already displayed success receipt, with no input. */
    public static synchronized void readSuccessCurrent(Context context) throws Exception {
        if (active != null) throw new IllegalStateException("trial-already-running");
        JSONObject report = new JSONObject(); String id = UUID.randomUUID().toString();
        report.put("id", id); report.put("status", "running"); report.put("currentPageOnly", true);
        report.put("checkoutEnabled", false); report.put("successReadOnly", true);
        report.put("computerExecutionRequired", false); report.put("writesProductionState", false);
        if (!ProbeState.putChecked(context, "trial", report)) throw new IllegalStateException("trial-save-failed");
        OneShotRunner run = new OneShotRunner(context, id, false, report); active = run;
        run.frameWindowId = run.windowId(); run.frameLayoutVersion = ProbeAccessibilityService.layoutVersion;
        run.frameRequestedElapsed = SystemClock.elapsedRealtime();
        MAIN.postDelayed(() -> { if (!run.finished) run.finish("trial-timeout"); }, 150000);
        ProbeAccessibilityService.captureFrame(context, (bitmap, capturedAt, code) -> {
            if (!run.current()) { if (bitmap != null) bitmap.recycle(); return; }
            if (bitmap == null) { run.finish("fresh-frame-failed"); return; }
            FrameOcr.recognizeCheckoutSuccess(bitmap, capturedAt, result -> {
                if (!run.current()) return;
                run.put("successModalRecognition", result.reason); run.put("successModalDiagnostics", result.diagnostics);
                run.put("successDate", nullable(result.date)); run.put("successCheckOut", nullable(result.checkOut));
                run.finish(run.validTargetFrame() && result.accepted ? "success-modal-read-only-verified" : "success-modal-unverified");
            });
        });
    }

    public static synchronized void cancel(Context context) {
        PendingIntent target = PendingIntent.getBroadcast(context, 101,
            new Intent(context, ProbeAlarmReceiver.class), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (target != null) {
            ((AlarmManager)context.getSystemService(Context.ALARM_SERVICE)).cancel(target);
            target.cancel();
        }
        if (active != null) active.finish("cancelled");
        else {
            JSONObject report = ProbeState.saved(context).optJSONObject("trial");
            if (report != null && "scheduled".equals(report.optString("status"))) {
                try { report.put("status", "cancelled"); } catch (Exception ignored) {}
                ProbeState.put(context, "trial", report);
            }
        }
    }

    static synchronized void onAlarm(Context context, String id) {
        JSONObject report = ProbeState.saved(context).optJSONObject("trial");
        if (report == null || !id.equals(report.optString("id")) ||
            !"scheduled".equals(report.optString("status")) || active != null) return;
        OneShotRunner run = new OneShotRunner(context, id, report.optBoolean("checkoutEnabled"), report);
        active = run;
        run.start();
    }

    private void start() {
        try {
            PowerManager power = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
            evidence.put("firedAt", System.currentTimeMillis());
            evidence.put("firedElapsed", SystemClock.elapsedRealtime());
            evidence.put("lockedAtStart", locked()); evidence.put("interactiveAtStart", power.isInteractive());
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            evidence.put("powerSourceAtStart", battery == null ? -1 : battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1));
            Intent usb = context.registerReceiver(null, new IntentFilter("android.hardware.usb.action.USB_STATE"));
            evidence.put("usbConnectedAtStart", usb == null ? JSONObject.NULL : usb.getBooleanExtra("connected", false));
            evidence.put("status", "running");
            if (!save()) { finish("trial-consumption-save-failed"); return; } // Consume before any input.
            if (Math.abs(System.currentTimeMillis() - evidence.optLong("scheduledAt")) > 120000) {
                finish("trial-expired"); return;
            }
            wake = power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "AutoDingResearch:one-shot");
            wake.acquire(155000);
            MAIN.postDelayed(() -> { if (!finished) finish("trial-timeout"); }, 150000);
            stage("unlock");
            ProbeAccessibilityService.requestNativeUnlock(context, (success, code) -> {
                if (!current()) return;
                put("unlockOutcome", code); put("unlockFinishedAt", System.currentTimeMillis());
                if (success) put("unlockedAt", System.currentTimeMillis());
                if (!success || locked()) { finish("native-unlock-failed"); return; }
                open();
            });
        } catch (Exception ignored) { finish("trial-start-failed"); }
    }

    private void open() {
        open(false);
    }
    private void open(boolean verification) {
        if (!current()) return;
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        if (service == null) { finish("service-not-connected"); return; }
        stage(verification ? "refresh-receipt-page" : "navigate");
        AttendanceNavigator.openAttendance(service, code -> {
            if (!current()) return;
            put(verification ? "verificationNavigationOutcome" : "navigationOutcome", code);
            if (!"attendance-page-ready".equals(code)) { finish("attendance-navigation-failed"); return; }
            MAIN.postDelayed(() -> capture(verification, 0), 2000);
        });
    }

    private void capture(boolean afterClick, int retry) {
        if (!current()) return;
        if (locked()) { finish("phone-relocked"); return; }
        stage(afterClick ? "verify-receipt" : "read-receipt");
        frameWindowId = windowId(); frameLayoutVersion = ProbeAccessibilityService.layoutVersion;
        frameRequestedElapsed = SystemClock.elapsedRealtime();
        ProbeAccessibilityService.captureFrame(context, (bitmap, capturedAt, code) -> {
            if (!current()) { if (bitmap != null) bitmap.recycle(); return; }
            if (bitmap == null) { put("captureOutcome", code); finish("fresh-frame-failed"); return; }
            FrameOcr.recognize(bitmap, capturedAt, result -> {
                if (!current()) return;
                if (!validFrame()) {
                    JSONObject rejected = new JSONObject();
                    try {
                        rejected.put("sameWindow", frameWindowId == windowId());
                        rejected.put("sameLayout", frameLayoutVersion == ProbeAccessibilityService.layoutVersion);
                        rejected.put("ageMs", SystemClock.elapsedRealtime() - frameRequestedElapsed);
                        rejected.put("phoneLocked", locked());
                        rejected.put("pageReady", ProbeAccessibilityService.active != null &&
                            "attendance-page-ready".equals(AttendanceNavigator.inspectAttendance(ProbeAccessibilityService.active)));
                    } catch (Exception ignored) {}
                    put("lastRejectedFrame", rejected);
                    if (retry < 2 && !locked()) {
                        put("frameRecaptureCount", evidence.optInt("frameRecaptureCount") + 1);
                        MAIN.postDelayed(() -> capture(afterClick, retry + 1), 500);
                    } else finish("frame-context-changed");
                    return;
                }
                put(afterClick ? "after" : "before", receipt(result));
                if (!result.accepted) { finish(afterClick ? "checkout-result-unverified" : "receipt-unverified"); return; }
                if (!afterClick) {
                    if (!checkout) { finish("read-only-verified"); return; }
                    click(result);
                } else {
                    if (newCheckout(result)) { finish("checkout-update-verified"); return; }
                    if (retry < 2) MAIN.postDelayed(() -> capture(true, retry + 1), 3000);
                    else finish("checkout-unchanged-or-unverified");
                }
            });
        });
    }

    private void click(FrameOcr.Result before) {
        if (!current()) return;
        if (System.currentTimeMillis() - before.freshTimeMs > 15000 || before.checkIn == null ||
            System.currentTimeMillis() < before.freshTimeMs ||
            SystemClock.elapsedRealtime() - frameRequestedElapsed > 15000 ||
            frameWindowId < 0 || frameWindowId != windowId() ||
            frameLayoutVersion != ProbeAccessibilityService.layoutVersion ||
            !ZonedDateTime.now().toLocalDate().toString().equals(before.date) ||
            before.actionBounds == null || !"下班打卡".equals(before.actionLabel)) {
            finish("checkout-context-unconfirmed"); return;
        }
        previousCheckout = before.checkOut;
        expectedDate = before.date; expectedCheckIn = before.checkIn;
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        if (service == null || locked()) { finish("checkout-window-unavailable"); return; }
        clickedAt = System.currentTimeMillis();
        try {
            evidence.put("clickRequestedAt", clickedAt); evidence.put("clickCount", 1);
            evidence.put("stage", "checkout-click-consumed");
        } catch (Exception ignored) { finish("click-consumption-save-failed"); return; }
        if (!save()) { finish("click-consumption-save-failed"); return; }
        AttendanceNavigator.clickCheckout(service, before.actionLabel, before.actionBounds, this::validFrame, code -> {
            if (!current()) return;
            put("clickOutcome", code);
            if (!"checkout-node-click-requested".equals(code) && !"checkout-gesture-completed".equals(code)) {
                finish("checkout-click-not-completed"); return;
            }
            MAIN.postDelayed(() -> inspectPostAction(0), 2500);
        });
    }

    /** The observed optional-reason sheet is part of this armed action only. */
    private void inspectPostAction(int retry) {
        if (!current()) return;
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        if (locked() || service == null) { finish("checkout-window-unavailable"); return; }
        if (evidence.optInt("earlyConfirmationCount") != 0 || clickedAt <= 0 ||
            System.currentTimeMillis() - clickedAt > 30000) {
            if ("attendance-page-ready".equals(AttendanceNavigator.inspectAttendance(service))) {
                capture(true, 0); return;
            }
            inspectSuccessModal(0);
            return;
        }
        stage("inspect-checkout-confirmation");
        frameWindowId = windowId(); frameLayoutVersion = ProbeAccessibilityService.layoutVersion;
        frameContentVersion = ProbeAccessibilityService.contentVersion;
        frameRequestedElapsed = SystemClock.elapsedRealtime();
        ProbeAccessibilityService.captureFrame(context, (bitmap, capturedAt, code) -> {
            if (!current()) { if (bitmap != null) bitmap.recycle(); return; }
            if (bitmap == null) { finish("checkout-confirmation-frame-failed"); return; }
            FrameOcr.recognizeEarlyCheckout(bitmap, capturedAt, result -> {
                if (!current()) return;
                if (!validConfirmationFrame()) {
                    if (retry < 2 && !locked() && evidence.optInt("earlyConfirmationCount") == 0)
                        MAIN.postDelayed(() -> inspectPostAction(retry + 1), 200);
                    else finish("checkout-confirmation-context-changed");
                    return;
                }
                put("earlyConfirmationRecognition", result.reason);
                put("earlyConfirmationDiagnostics", result.diagnostics);
                if (!result.accepted || result.bounds == null || !"早退打卡".equals(result.label)) {
                    if ("attendance-page-ready".equals(AttendanceNavigator.inspectAttendance(service))) capture(true, 0);
                    else inspectSuccessModal(0);
                    return;
                }
                try {
                    evidence.put("earlyConfirmationCount", 1);
                    evidence.put("earlyConfirmationRequestedAt", System.currentTimeMillis());
                    evidence.put("stage", "early-confirmation-consumed");
                } catch (Exception ignored) { finish("confirmation-consumption-save-failed"); return; }
                if (!save()) { finish("confirmation-consumption-save-failed"); return; }
                AttendanceNavigator.confirmEarlyCheckout(service, result.label, result.bounds,
                    this::validConfirmationFrame, outcome -> {
                        if (!current()) return;
                        put("earlyConfirmationOutcome", outcome);
                        if (!"early-checkout-gesture-completed".equals(outcome) &&
                            !"early-checkout-node-click-requested".equals(outcome)) {
                            finish("checkout-confirmation-not-completed"); return;
                        }
                        MAIN.postDelayed(() -> inspectPostAction(0), 3500);
                    });
            });
        });
    }

    private void inspectSuccessModal(int retry) {
        if (!current()) return;
        if (evidence.optInt("viewResultCount") != 0) { finish("result-navigation-already-consumed"); return; }
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        if (service == null || locked()) { finish("checkout-window-unavailable"); return; }
        stage("read-success-modal");
        frameWindowId = windowId(); frameLayoutVersion = ProbeAccessibilityService.layoutVersion;
        frameContentVersion = ProbeAccessibilityService.contentVersion;
        frameRequestedElapsed = SystemClock.elapsedRealtime();
        ProbeAccessibilityService.captureFrame(context, (bitmap, capturedAt, code) -> {
            if (!current()) { if (bitmap != null) bitmap.recycle(); return; }
            if (bitmap == null) { finish("success-modal-frame-failed"); return; }
            FrameOcr.recognizeCheckoutSuccess(bitmap, capturedAt, result -> {
                if (!current()) return;
                if (!validSuccessFrame()) {
                    if (retry < 2 && !locked()) MAIN.postDelayed(() -> inspectSuccessModal(retry + 1), 300);
                    else finish("success-modal-context-changed");
                    return;
                }
                put("successModalRecognition", result.reason); put("successModalDiagnostics", result.diagnostics);
                if (!result.accepted || result.actionBounds == null) {
                    if (retry < 2) MAIN.postDelayed(() -> inspectSuccessModal(retry + 1), 1500);
                    else finish("success-modal-unconfirmed");
                    return;
                }
                if (!newCheckoutTime(result.date, result.checkOut, result.freshTimeMs)) {
                    finish("success-modal-time-unconfirmed"); return;
                }
                try {
                    JSONObject modal = new JSONObject();
                    modal.put("date", result.date); modal.put("checkOut", result.checkOut);
                    modal.put("capturedAt", result.freshTimeMs); evidence.put("successModal", modal);
                    evidence.put("viewResultCount", 1); evidence.put("stage", "view-result-consumed");
                } catch (Exception ignored) { finish("view-result-consumption-save-failed"); return; }
                if (!save()) { finish("view-result-consumption-save-failed"); return; }
                AttendanceNavigator.viewCheckoutResultAndReturn(service, result.actionBounds, this::validSuccessFrame, outcome -> {
                    if (!current()) return;
                    put("viewResultOutcome", outcome);
                    if (!"checkout-result-returned".equals(outcome)) { finish("result-navigation-unconfirmed"); return; }
                    open(true);
                });
            });
        });
    }

    private boolean newCheckout(FrameOcr.Result after) {
        try {
            return expectedCheckIn.equals(after.checkIn) &&
                newCheckoutTime(after.date, after.checkOut, after.freshTimeMs);
        } catch (Exception ignored) { return false; }
    }
    private boolean newCheckoutTime(String date, String checkOut, long capturedAt) {
        try {
            if (checkOut == null || checkOut.equals(previousCheckout) ||
                !expectedDate.equals(date) || capturedAt < clickedAt) return false;
            ZoneId zone = ZoneId.systemDefault();
            ZonedDateTime clicked = Instant.ofEpochMilli(clickedAt).atZone(zone);
            long actual = clicked.toLocalDate().atTime(LocalTime.parse(checkOut)).atZone(zone).toInstant().toEpochMilli();
            return actual >= (clickedAt / 60000) * 60000 && actual <= System.currentTimeMillis();
        } catch (Exception ignored) { return false; }
    }

    private JSONObject receipt(FrameOcr.Result result) {
        JSONObject record = new JSONObject();
        try {
            record.put("accepted", result.accepted); record.put("reason", result.reason);
            record.put("date", nullable(result.date)); record.put("checkIn", nullable(result.checkIn));
            record.put("checkOut", nullable(result.checkOut)); record.put("actionLabel", nullable(result.actionLabel));
            record.put("capturedAt", result.freshTimeMs);
            record.put("diagnostics", result.diagnostics);
        } catch (Exception ignored) {}
        return record;
    }
    private static Object nullable(String value) { return value == null ? JSONObject.NULL : value; }
    private boolean validFrame() {
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        return validTargetFrame() &&
            "attendance-page-ready".equals(AttendanceNavigator.inspectAttendance(service));
    }
    private boolean validConfirmationFrame() {
        return checkout && clickedAt > 0 && expectedDate != null &&
            expectedDate.equals(ZonedDateTime.now().toLocalDate().toString()) &&
            System.currentTimeMillis() >= clickedAt && System.currentTimeMillis() - clickedAt <= 30000 &&
            frameContentVersion == ProbeAccessibilityService.contentVersion &&
            validTargetFrame();
    }
    private boolean validSuccessFrame() {
        // The result link only navigates. Background clocks emit text updates
        // while this modal stays fixed; retain fresh window/layout binding.
        return checkout && clickedAt > 0 && expectedDate != null &&
            expectedDate.equals(ZonedDateTime.now().toLocalDate().toString()) &&
            validTargetFrame();
    }
    private boolean validTargetFrame() {
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        if (!(current() && !locked() && service != null && frameWindowId >= 0 &&
            frameWindowId == windowId() && frameLayoutVersion == ProbeAccessibilityService.layoutVersion &&
            SystemClock.elapsedRealtime() - frameRequestedElapsed <= 15000)) return false;
        android.view.accessibility.AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return false;
        try { return AttendanceNavigator.DING_PACKAGE.contentEquals(root.getPackageName() == null ? "" : root.getPackageName()); }
        finally { root.recycle(); }
    }
    private int windowId() {
        ProbeAccessibilityService service = ProbeAccessibilityService.active;
        if (service == null) return -1;
        android.view.accessibility.AccessibilityNodeInfo root = service.getRootInActiveWindow();
        if (root == null) return -1;
        try { return root.getWindowId(); } finally { root.recycle(); }
    }
    private boolean locked() { return ((KeyguardManager)context.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked(); }
    private boolean current() { return !finished && active == this && SystemClock.elapsedRealtime() < deadline; }
    private void stage(String value) { put("stage", value); }
    private void put(String key, Object value) { try { evidence.put(key, value); save(); } catch (Exception ignored) {} }
    private boolean save() { return ProbeState.putChecked(context, "trial", evidence); }
    private synchronized void finish(String outcome) {
        if (finished) return;
        finished = true;
        NativeUnlock.cancel();
        AttendanceNavigator.cancel();
        try { evidence.put("outcome", outcome); evidence.put("status", "finished"); evidence.put("finishedAt", System.currentTimeMillis()); save(); }
        catch (Exception ignored) {}
        if (wake != null && wake.isHeld()) wake.release();
        synchronized (OneShotRunner.class) { if (active == this) active = null; }
    }
}

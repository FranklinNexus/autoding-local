/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.autoding.research;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.Path;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.WindowManager;
import android.util.DisplayMetrics;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

/**
 * Finite, foreground-only DingTalk navigation. No raw UI values are returned or
 * stored. An accepted click is a navigation request, never an attendance receipt.
 */
public final class AttendanceNavigator {
    public static final String DING_PACKAGE = "com.alibaba.android.rimet";
    private static final String SHORTCUT_ID = DING_PACKAGE + ":id/im_ding_kit_item_txt";
    private static final String TAB_ID = DING_PACKAGE + ":id/h5_tabbaritem_txticon";
    private static final String EXPIRED_MESSAGE = "页面停留时间过长，请退出考勤重新进入";
    private static final int NODE_LIMIT = 1024;
    private static final long NAVIGATION_TIMEOUT_MS = 24000;
    private static final long POLL_MS = 500;
    private static final long CONTEXT_MAX_AGE_MS = 10000;
    private static final ZoneId OFFICE_ZONE = ZoneId.of("Asia/Shanghai");
    private static final AtomicBoolean CHECKOUT_IN_FLIGHT = new AtomicBoolean();
    private static final AtomicLong CANCEL_EPOCH = new AtomicLong();
    private static volatile Navigation activeNavigation;

    public interface Callback { void onResult(String stableCode); }

    /** Fresh OCR context supplied by the caller; construction alone proves nothing. */
    public static final class CheckoutContext {
        public final String workDate;
        public final long observedElapsedRealtime;
        public final boolean checkoutActionConfirmed;
        public final boolean checkoutRecordConfirmed;
        public final boolean allowUpdate;
        public final boolean mutationArmed;

        public CheckoutContext(String workDate, long observedElapsedRealtime,
                boolean checkoutActionConfirmed, boolean checkoutRecordConfirmed,
                boolean allowUpdate, boolean mutationArmed) {
            this.workDate = workDate;
            this.observedElapsedRealtime = observedElapsedRealtime;
            this.checkoutActionConfirmed = checkoutActionConfirmed;
            this.checkoutRecordConfirmed = checkoutRecordConfirmed;
            this.allowUpdate = allowUpdate;
            this.mutationArmed = mutationArmed;
        }
    }

    private AttendanceNavigator() {}

    /**
     * Invalidates queued navigation/input immediately. A gesture already accepted
     * by Android is left alone; its completion is ignored, with no extra gesture.
     */
    public static void cancel() {
        CANCEL_EPOCH.incrementAndGet();
        Navigation navigation = activeNavigation;
        if (navigation != null) navigation.handler.post(navigation::cancelNow);
    }

    /**
     * Starts DingTalk through its installed launcher when needed, then uses the
     * already-observed, unique home shortcut. Unknown workbench layouts stop.
     * Caller receives exactly one fixed result on the main thread.
     */
    public static void openAttendance(AccessibilityService service, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback-required");
        new Navigation(service, callback).start();
    }

    /** Read-only page check; the caller may use it before taking a fresh screenshot. */
    public static String inspectAttendance(AccessibilityService service) {
        String guard = guard(service);
        if (guard != null) return guard;
        try (Window window = Window.read(service)) {
            if (!window.targetForeground) return "dingtalk-not-foreground";
            if (!window.complete) return "window-scan-incomplete";
            return window.attendancePage() ? "attendance-page-ready" : "attendance-page-unconfirmed";
        } catch (RuntimeException ignored) { return "window-read-failed"; }
    }

    /**
     * A single explicitly armed checkout action. Caller must first bind the exact
     * OCR label and bounds to a fresh screenshot, today's page, and checkout OCR
     * context. This method does not press any confirmation dialog or verify a
     * receipt. Dispatch/gesture completion only means the input was delivered.
     */
    public static void clickCheckout(AccessibilityService service, String actionLabel,
            Rect actionBounds, Callback callback) {
        clickCheckoutInternal(service, actionLabel, actionBounds, null, () -> true, callback);
    }

    /** Rechecks caller-owned frame freshness in the actual queued input operation. */
    public static void clickCheckout(AccessibilityService service, String actionLabel,
            Rect actionBounds, BooleanSupplier freshness, Callback callback) {
        clickCheckoutInternal(service, actionLabel, actionBounds, null, freshness, callback);
    }

    /** Generic 更新打卡 additionally requires a fresh, explicit checkout record context. */
    public static void clickCheckout(AccessibilityService service, String actionLabel,
            Rect actionBounds, CheckoutContext context, Callback callback) {
        clickCheckoutInternal(service, actionLabel, actionBounds, context, () -> true, callback);
    }

    /**
     * One explicitly armed continuation of a recognized checkout sheet. The
     * supplier must bind today's armed checkout and the unique exact OCR label
     * to this fresh sheet/frame; a generic confirmation is never accepted here.
     */
    public static void confirmEarlyCheckout(AccessibilityService service, String exactLabel,
            Rect ocrBounds, BooleanSupplier freshness, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback-required");
        final Rect supplied = ocrBounds == null ? null : new Rect(ocrBounds);
        final long epoch = CANCEL_EPOCH.get();
        Handler main = new Handler(Looper.getMainLooper());
        if (!CHECKOUT_IN_FLIGHT.compareAndSet(false, true)) {
            main.post(() -> {
                if (CANCEL_EPOCH.get() == epoch) callback.onResult("checkout-action-already-running");
            }); return;
        }
        AtomicBoolean finished = new AtomicBoolean();
        Callback once = code -> {
            if (finished.compareAndSet(false, true)) {
                CHECKOUT_IN_FLIGHT.set(false);
                if (CANCEL_EPOCH.get() == epoch) callback.onResult(code);
            }
        };
        main.post(() -> {
            if (CANCEL_EPOCH.get() != epoch) { once.onResult("early-checkout-cancelled"); return; }
            if (!fresh(freshness)) { once.onResult("early-checkout-context-expired"); return; }
            String blocked = guard(service);
            if (blocked != null) { once.onResult(blocked); return; }
            if (!"早退打卡".equals(exactLabel)) { once.onResult("early-checkout-label-rejected"); return; }
            if (!"JEF-AN00".equals(Build.MODEL) || Build.VERSION.SDK_INT != 31) {
                once.onResult("layout-acceptance-required"); return;
            }
            try (Window window = Window.read(service)) {
                if (!earlyWindow(window, supplied, service)) {
                    once.onResult("early-checkout-window-unconfirmed"); return;
                }
                if (window.earlyLabels.size() > 1) { once.onResult("early-checkout-label-ambiguous"); return; }
                if (window.earlyLabels.size() == 1) {
                    AccessibilityNodeInfo label = window.earlyLabels.get(0);
                    Rect actual = new Rect(); label.getBoundsInScreen(actual);
                    if (!actual.contains(supplied.centerX(), supplied.centerY())) {
                        once.onResult("early-checkout-bounds-mismatch"); return;
                    }
                    AccessibilityNodeInfo node = clickable(label, window.rootBounds);
                    if (node != null) {
                        try {
                            if (CANCEL_EPOCH.get() != epoch || !fresh(freshness)) {
                                once.onResult("early-checkout-context-expired"); return;
                            }
                            if (guard(service) != null) { once.onResult("phone-locked"); return; }
                            once.onResult(node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                ? "early-checkout-node-click-requested" : "early-checkout-node-click-rejected");
                        } finally { node.recycle(); }
                        return;
                    }
                }
                // A second live read binds the opaque sheet to the same WebView.
                try (Window current = Window.read(service)) {
                    if (!earlyWindow(current, supplied, service) || current.windowId != window.windowId
                            || !current.rootBounds.equals(window.rootBounds)
                            || !current.webViewBounds.get(0).equals(window.webViewBounds.get(0))) {
                        once.onResult("early-checkout-window-changed"); return;
                    }
                }
                Path path = new Path(); path.moveTo(supplied.exactCenterX(), supplied.exactCenterY());
                GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
                if (CANCEL_EPOCH.get() != epoch || !fresh(freshness)) {
                    once.onResult("early-checkout-context-expired"); return;
                }
                blocked = guard(service);
                if (blocked != null) { once.onResult(blocked); return; }
                boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) {
                        once.onResult("early-checkout-gesture-completed");
                    }
                    @Override public void onCancelled(GestureDescription description) {
                        once.onResult("early-checkout-gesture-cancelled");
                    }
                }, main);
                if (!accepted) once.onResult("early-checkout-gesture-rejected");
                else main.postDelayed(() -> once.onResult("early-checkout-gesture-timeout"), 3000);
            } catch (RuntimeException ignored) { once.onResult("early-checkout-failed"); }
        });
    }

    /** Caller proves a fresh, dated checkout-success modal and unique result-link OCR. */
    public static void viewCheckoutResultAndReturn(AccessibilityService service, Rect ocrBounds,
            BooleanSupplier freshness, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback-required");
        final long epoch = CANCEL_EPOCH.get();
        if (!CHECKOUT_IN_FLIGHT.compareAndSet(false, true)) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (CANCEL_EPOCH.get() == epoch) callback.onResult("checkout-action-already-running");
            }); return;
        }
        new ResultReturn(service, ocrBounds, freshness, callback, epoch).start();
    }

    private static final class ResultReturn implements Runnable {
        final AccessibilityService service;
        final Rect supplied;
        final BooleanSupplier freshness;
        final Callback callback;
        final long epoch;
        final Handler handler = new Handler(Looper.getMainLooper());
        boolean finished, gestureComplete, backRequested, secondBackRequested;
        long deadline;
        int originWindowId;
        final Rect originRootBounds = new Rect(), originWebViewBounds = new Rect();
        ResultReturn(AccessibilityService service, Rect bounds, BooleanSupplier freshness, Callback callback, long epoch) {
            this.service = service; this.supplied = bounds == null ? null : new Rect(bounds);
            this.freshness = freshness; this.callback = callback; this.epoch = epoch;
        }
        void start() { handler.post(this::dispatch); }
        void dispatch() {
            if (CANCEL_EPOCH.get() != epoch) { finish("checkout-result-cancelled"); return; }
            String blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            if (!fresh(freshness)) { finish("checkout-result-context-expired"); return; }
            try (Window window = Window.read(service)) {
                if (!earlyWindow(window, supplied, service)
                        || !new Rect(300, 1990, 800, 2120).contains(supplied)) {
                    finish("checkout-result-window-unconfirmed"); return;
                }
                originWindowId = window.windowId;
                originRootBounds.set(window.rootBounds);
                originWebViewBounds.set(window.webViewBounds.get(0));
                Path path = new Path(); path.moveTo(supplied.exactCenterX(), supplied.exactCenterY());
                GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
                if (CANCEL_EPOCH.get() != epoch || !fresh(freshness)) {
                    finish("checkout-result-context-expired"); return;
                }
                blocked = guard(service);
                if (blocked != null) { finish(blocked); return; }
                boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) {
                        if (finished) return;
                        if (CANCEL_EPOCH.get() != epoch) { finish("checkout-result-cancelled"); return; }
                        gestureComplete = true;
                        deadline = SystemClock.elapsedRealtime() + 10000;
                        handler.post(ResultReturn.this);
                    }
                    @Override public void onCancelled(GestureDescription description) {
                        finish("checkout-result-gesture-cancelled");
                    }
                }, handler);
                if (!accepted) finish("checkout-result-gesture-rejected");
                else handler.postDelayed(() -> {
                    if (!finished && !gestureComplete) finish("checkout-result-gesture-timeout");
                }, 3000);
            } catch (RuntimeException ignored) { finish("checkout-result-navigation-failed"); }
        }
        @Override public void run() {
            if (finished) return;
            if (CANCEL_EPOCH.get() != epoch) { finish("checkout-result-cancelled"); return; }
            String blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            if (SystemClock.elapsedRealtime() > deadline) {
                finish(backRequested ? "checkout-result-return-timeout" : "checkout-result-statistics-timeout"); return;
            }
            try (Window window = Window.read(service)) {
                if (window.targetForeground && window.complete && window.passwordNodes == 0) {
                    if (!backRequested && window.statsTitles.size() > 1) {
                        finish("checkout-result-statistics-ambiguous"); return;
                    }
                    if (!backRequested && window.statsTitles.size() == 1) {
                        AccessibilityNodeInfo title = window.statsTitles.get(0);
                        if (!title.refresh() || !DING_PACKAGE.equals(text(title.getPackageName()))
                                || !(DING_PACKAGE + ":id/title").equals(text(title.getViewIdResourceName()))
                                || !"统计".equals(text(title.getText())) || !title.isVisibleToUser() || !title.isEnabled()) {
                            finish("checkout-result-statistics-changed"); return;
                        }
                        if (CANCEL_EPOCH.get() != epoch) { finish("checkout-result-cancelled"); return; }
                        backRequested = true;
                        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                            finish("checkout-result-back-rejected"); return;
                        }
                        deadline = SystemClock.elapsedRealtime() + 5000;
                    } else if (backRequested && window.dialogMessages == 0) {
                        if (window.attendancePage() || window.shortcuts.size() == 1 || window.readOnlyNotificationPage()) {
                            finish("checkout-result-returned"); return;
                        }
                        if (!secondBackRequested && window.statsTitles.isEmpty() && window.windowId == originWindowId
                                && window.rootBounds.equals(originRootBounds)
                                && window.webViewBounds.size() == 1
                                && window.webViewBounds.get(0).equals(originWebViewBounds)) {
                            if (CANCEL_EPOCH.get() != epoch) { finish("checkout-result-cancelled"); return; }
                            secondBackRequested = true;
                            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                                finish("checkout-result-second-back-rejected"); return;
                            }
                            deadline = SystemClock.elapsedRealtime() + 5000;
                        }
                    }
                }
                handler.postDelayed(this, 250);
            } catch (RuntimeException ignored) { finish("checkout-result-navigation-failed"); }
        }
        void finish(String code) {
            if (finished) return;
            finished = true; handler.removeCallbacksAndMessages(null);
            CHECKOUT_IN_FLIGHT.set(false);
            if (CANCEL_EPOCH.get() == epoch) callback.onResult(code);
        }
    }

    private static boolean earlyWindow(Window window, Rect bounds, AccessibilityService service) {
        if (!window.targetForeground || !window.complete || window.passwordNodes != 0
                || window.dialogMessages != 0 || window.webViewBounds.size() != 1
                || !validActionBounds(service, bounds, window.rootBounds)) return false;
        WindowManager manager = (WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
        if (manager == null) return false;
        DisplayMetrics screen = new DisplayMetrics(); manager.getDefaultDisplay().getRealMetrics(screen);
        return screen.widthPixels > 0 && screen.heightPixels > 0
            && Math.abs(screen.widthPixels / (double)screen.heightPixels - 1080.0 / 2400.0) <= 0.005
            && bounds.top >= screen.heightPixels * 0.60 && bounds.bottom <= screen.heightPixels * 0.97
            && window.webViewBounds.get(0).contains(bounds);
    }

    private static void clickCheckoutInternal(AccessibilityService service, String actionLabel,
            Rect actionBounds, CheckoutContext context, BooleanSupplier freshness, Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback-required");
        final Rect supplied = actionBounds == null ? null : new Rect(actionBounds);
        final long epoch = CANCEL_EPOCH.get();
        Handler main = new Handler(Looper.getMainLooper());
        if (!CHECKOUT_IN_FLIGHT.compareAndSet(false, true)) {
            main.post(() -> {
                if (CANCEL_EPOCH.get() == epoch) callback.onResult("checkout-action-already-running");
            }); return;
        }
        AtomicBoolean finished = new AtomicBoolean();
        Callback once = code -> {
            if (finished.compareAndSet(false, true)) {
                CHECKOUT_IN_FLIGHT.set(false);
                if (CANCEL_EPOCH.get() == epoch) callback.onResult(code);
            }
        };
        main.post(() -> {
            if (CANCEL_EPOCH.get() != epoch) { once.onResult("checkout-operation-cancelled"); return; }
            if (!fresh(freshness)) { once.onResult("checkout-context-expired"); return; }
            String blocked = guard(service);
            if (blocked != null) { once.onResult(blocked); return; }
            boolean update = "更新打卡".equals(actionLabel);
            if (!"下班打卡".equals(actionLabel) && !update) {
                once.onResult("checkout-action-label-rejected"); return;
            }
            if (context != null) {
                blocked = contextGuard(context);
                if (blocked != null) { once.onResult(blocked); return; }
                if (!context.mutationArmed) { once.onResult("checkout-read-only"); return; }
            }
            if (update && (context == null || !context.allowUpdate || !context.checkoutRecordConfirmed)) {
                once.onResult("checkout-update-context-required"); return;
            }
            try (Window window = Window.read(service)) {
                if (!window.targetForeground) { once.onResult("dingtalk-not-foreground"); return; }
                if (!window.complete) { once.onResult("window-scan-incomplete"); return; }
                if (!window.attendancePage()) { once.onResult("attendance-page-unconfirmed"); return; }
                if (!validActionBounds(service, supplied, window.rootBounds)) {
                    once.onResult("checkout-action-bounds-rejected"); return;
                }
                List<AccessibilityNodeInfo> matches = update ? window.updateLabels : window.checkoutLabels;
                if (matches.size() > 1) { once.onResult("checkout-action-ambiguous"); return; }
                if (matches.size() == 1) {
                    Rect nodeBounds = new Rect(); matches.get(0).getBoundsInScreen(nodeBounds);
                    if (!nodeBounds.contains(supplied.centerX(), supplied.centerY())) {
                        once.onResult("checkout-action-bounds-mismatch"); return;
                    }
                    AccessibilityNodeInfo target = clickable(matches.get(0), window.rootBounds);
                    if (target != null) {
                        try {
                            if (CANCEL_EPOCH.get() != epoch) { once.onResult("checkout-operation-cancelled"); return; }
                            if (!fresh(freshness)) { once.onResult("checkout-context-expired"); return; }
                            once.onResult(target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                                ? "checkout-node-click-requested" : "checkout-node-click-rejected");
                        } finally { target.recycle(); }
                        return;
                    }
                }
                Path path = new Path(); path.moveTo(supplied.exactCenterX(), supplied.exactCenterY());
                GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
                if (CANCEL_EPOCH.get() != epoch) { once.onResult("checkout-operation-cancelled"); return; }
                if (!fresh(freshness)) { once.onResult("checkout-context-expired"); return; }
                boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription description) {
                        once.onResult("checkout-gesture-completed");
                    }
                    @Override public void onCancelled(GestureDescription description) {
                        once.onResult("checkout-gesture-cancelled");
                    }
                }, main);
                if (!accepted) once.onResult("checkout-gesture-rejected");
                else main.postDelayed(() -> once.onResult("checkout-gesture-callback-timeout"), 3000);
            } catch (RuntimeException ignored) { once.onResult("checkout-action-failed"); }
        });
    }

    private static boolean fresh(BooleanSupplier freshness) {
        try { return freshness != null && freshness.getAsBoolean(); }
        catch (RuntimeException ignored) { return false; }
    }

    private static boolean validActionBounds(AccessibilityService service, Rect bounds, Rect rootBounds) {
        if (bounds == null || bounds.isEmpty() || bounds.width() < 6 || bounds.height() < 8
                || rootBounds.isEmpty() || !rootBounds.contains(bounds)) return false;
        WindowManager windows = (WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
        if (windows == null) return false;
        DisplayMetrics metrics = new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
        Rect screen = new Rect(0, 0, metrics.widthPixels, metrics.heightPixels);
        return screen.contains(bounds) && bounds.width() <= screen.width() * 0.7
            && bounds.height() <= screen.height() * 0.2;
    }

    private static String contextGuard(CheckoutContext context) {
        if (context == null || !context.checkoutActionConfirmed) return "checkout-context-required";
        long age = SystemClock.elapsedRealtime() - context.observedElapsedRealtime;
        if (context.observedElapsedRealtime <= 0 || age < 0 || age > CONTEXT_MAX_AGE_MS)
            return "checkout-context-expired";
        try {
            if (!LocalDate.parse(context.workDate).equals(LocalDate.now(OFFICE_ZONE)))
                return "checkout-date-mismatch";
        } catch (RuntimeException ignored) { return "checkout-date-unconfirmed"; }
        if (context.allowUpdate && !context.checkoutRecordConfirmed) return "checkout-update-context-required";
        return null;
    }

    private static String guard(AccessibilityService service) {
        if (service == null) return "accessibility-service-unavailable";
        try {
            KeyguardManager keyguard = (KeyguardManager)service.getSystemService(Context.KEYGUARD_SERVICE);
            if (keyguard == null) return "keyguard-state-unavailable";
            if (keyguard.isKeyguardLocked()) return "phone-locked";
            return null;
        } catch (RuntimeException ignored) { return "keyguard-state-unavailable"; }
    }

    private static final class Navigation implements Runnable {
        private final AccessibilityService service;
        private final Callback callback;
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final long epoch = CANCEL_EPOCH.get();
        private long deadline;
        private boolean finished, launcherRequested, shortcutRequested, launcherIconRequested;
        private boolean exitRequested, observedExit, expiredDismissed;
        private boolean notificationBackRequested;
        private boolean normalizationRequested, normalizationComplete;
        private boolean normalizationWaitScheduled;
        private boolean launcherHomeReset, launcherPageRequested, launcherTransitionPending;
        private boolean launcherPageGesturePending;
        private long launcherReadAfter;

        Navigation(AccessibilityService service, Callback callback) {
            this.service = service; this.callback = callback;
        }
        void start() {
            handler.post(() -> {
                if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
                Navigation previous = activeNavigation;
                if (previous != null && previous != this) previous.cancelNow();
                activeNavigation = this;
                deadline = SystemClock.elapsedRealtime() + NAVIGATION_TIMEOUT_MS;
                run();
            });
        }
        @Override public void run() {
            if (finished) return;
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            String blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            if (SystemClock.elapsedRealtime() > deadline) {
                finish(timeoutCode()); return;
            }
            try (Window window = Window.read(service)) {
                if (!window.targetForeground) {
                    if (!launcherRequested) {
                        Intent launch = service.getPackageManager().getLaunchIntentForPackage(DING_PACKAGE);
                        if (launch == null) { finish("dingtalk-launcher-unavailable"); return; }
                        launch.setPackage(DING_PACKAGE);
                        if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
                        service.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                        launcherRequested = true;
                    } else if (window.launcherForeground && !launcherIconRequested) {
                        if (!window.complete) { finish("launcher-window-scan-incomplete"); return; }
                        if (launcherPageGesturePending) return;
                        if (launcherTransitionPending) {
                            long remaining = launcherReadAfter - SystemClock.elapsedRealtime();
                            if (remaining > 0) { handler.postDelayed(this, remaining); return; }
                            launcherTransitionPending = false;
                        }
                        String profileError = launcherHomeReset ? launcherProfileError(window, true) : null;
                        if (profileError != null) {
                            finish(profileError); return;
                        }
                        if (window.launcherIcons.size() > 1) { finish("dingtalk-launcher-icon-ambiguous"); return; }
                        if (window.launcherIcons.size() == 1) {
                            openLauncherIcon(window); return;
                        }
                        searchLauncherPage(window); return;
                    }
                    again(); return;
                }
                if (!window.complete) { finish("window-scan-incomplete"); return; }
                if (window.dialogMessages > 0) {
                    if (window.expiredMessages != 1 || window.dialogMessages != 1
                            || window.expiredConfirm.size() != 1 || expiredDismissed) {
                        finish("attendance-native-dialog-unconfirmed"); return;
                    }
                    if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
                    AccessibilityNodeInfo positive = window.expiredConfirm.get(0);
                    if (!positive.refresh() || !DING_PACKAGE.equals(text(positive.getPackageName()))
                            || !"android:id/button1".equals(text(positive.getViewIdResourceName()))
                            || !"确定".equals(text(positive.getText())) || !positive.isVisibleToUser()
                            || !positive.isEnabled() || !positive.isClickable()) {
                        finish("attendance-expired-dialog-changed"); return;
                    }
                    expiredDismissed = true;
                    if (!positive.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        finish("attendance-expired-dialog-click-rejected"); return;
                    }
                    again(); return;
                }
                if (window.attendancePage()) {
                    if (shortcutRequested && (!exitRequested || observedExit)) {
                        if (normalizationComplete) finish("attendance-page-ready");
                        else if (!normalizationRequested && !normalizationWaitScheduled) waitForHeader();
                        return;
                    }
                    // A new screenshot does not renew an existing WebView session.
                    // Re-enter only through the observed home attendance shortcut.
                    if (!exitRequested) {
                        if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
                        exitRequested = true;
                        if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                            finish("attendance-exit-back-rejected"); return;
                        }
                    }
                    again(); return;
                }
                if (exitRequested) observedExit = true;
                if (!shortcutRequested && !notificationBackRequested && window.readOnlyNotificationPage()) {
                    if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
                    notificationBackRequested = true;
                    if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
                        finish("notification-back-rejected"); return;
                    }
                    again(); return;
                }
                if (!shortcutRequested && window.shortcuts.size() == 1) {
                    if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
                    if (!click(window.shortcuts.get(0), window.rootBounds)) {
                        finish("navigation-click-not-supported"); return;
                    }
                    shortcutRequested = true;
                } else if (window.shortcuts.size() > 1) { finish("attendance-entry-ambiguous"); return; }
                again();
            } catch (RuntimeException ignored) { finish("attendance-navigation-failed"); }
        }
        private String timeoutCode() {
            if (normalizationRequested || normalizationWaitScheduled) return "attendance-header-timeout";
            if (shortcutRequested) return "attendance-shortcut-no-page";
            if (exitRequested) return "attendance-reentry-not-found";
            if (launcherIconRequested) return "dingtalk-launcher-icon-no-foreground";
            if (launcherPageRequested) return "dingtalk-launcher-two-page-search-timeout";
            if (launcherHomeReset) return "dingtalk-launcher-home-search-timeout";
            return launcherRequested ? "dingtalk-launch-no-foreground" : "attendance-entry-not-found";
        }
        private String launcherProfileError(Window window, boolean inspectWorkspace) {
            if (!"JEF-AN00".equals(Build.MODEL) || Build.VERSION.SDK_INT != 31)
                return "launcher-profile-device-unverified";
            if (!"com.huawei.android.launcher".equals(window.rootPackage))
                return "launcher-profile-package-unverified";
            WindowManager manager = (WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
            if (manager == null) return "launcher-profile-display-unavailable";
            DisplayMetrics display = new DisplayMetrics(); manager.getDefaultDisplay().getRealMetrics(display);
            if (display.widthPixels != 1080 || display.heightPixels != 2400)
                return "launcher-profile-display-unverified";
            if (!window.rootBounds.equals(new Rect(0, 0, 1080, 2400)))
                return "launcher-profile-root-bounds-unverified";
            if (!inspectWorkspace || window.launcherWorkspaces.isEmpty()) return null;
            if (window.launcherWorkspaces.size() != 1) return "launcher-workspace-ambiguous";
            AccessibilityNodeInfo workspace = window.launcherWorkspaces.get(0);
            Rect bounds = new Rect(); workspace.getBoundsInScreen(bounds);
            String description = text(workspace.getContentDescription()).replaceAll("\\s+", "");
            if (!bounds.equals(new Rect(0, 0, 1080, 2400))) return "launcher-workspace-bounds-unverified";
            return description.matches("第[12]屏，共2屏") ? null : "launcher-workspace-page-count-unverified";
        }
        private void launcherWait() {
            launcherTransitionPending = true;
            launcherReadAfter = SystemClock.elapsedRealtime() + 800;
            handler.postDelayed(this, 800);
        }
        private void searchLauncherPage(Window window) {
            String profileError = launcherProfileError(window, launcherHomeReset);
            if (profileError != null) { finish(profileError); return; }
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            String blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            if (!launcherHomeReset) {
                launcherHomeReset = true;
                if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                    finish("dingtalk-launcher-home-rejected"); return;
                }
                launcherWait(); return;
            }
            if (launcherPageRequested) { finish("dingtalk-launcher-icon-not-found-two-pages"); return; }
            launcherPageRequested = true; // Exactly one observed page transition.
            // The native service may omit the non-important workspace container.
            // The observed device/root/display profile still permits one exact swipe.
            AccessibilityNodeInfo workspace = window.launcherWorkspaces.isEmpty() ? null : window.launcherWorkspaces.get(0);
            if (workspace != null && workspace.isScrollable() && (workspace.getActions() & AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) != 0
                    && workspace.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) {
                launcherWait(); return;
            }
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            Path path = new Path(); path.moveTo(850, 1500); path.lineTo(250, 1500);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 450)).build();
            launcherPageGesturePending = true;
            boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    if (finished || CANCEL_EPOCH.get() != epoch) return;
                    launcherPageGesturePending = false;
                    launcherWait();
                }
                @Override public void onCancelled(GestureDescription description) {
                    if (finished || CANCEL_EPOCH.get() != epoch) return;
                    finish("dingtalk-launcher-page-gesture-cancelled");
                }
            }, handler);
            if (!accepted) finish("dingtalk-launcher-page-gesture-rejected");
            else handler.postDelayed(() -> {
                if (!finished && CANCEL_EPOCH.get() == epoch && launcherPageGesturePending)
                    finish("dingtalk-launcher-page-gesture-timeout");
            }, 3000);
        }
        private void waitForHeader() {
            normalizationWaitScheduled = true;
            handler.postDelayed(() -> {
                if (finished || CANCEL_EPOCH.get() != epoch) return;
                if (SystemClock.elapsedRealtime() > deadline) {
                    finish(timeoutCode()); return;
                }
                String blocked = guard(service);
                if (blocked != null) { finish(blocked); return; }
                try (Window current = Window.read(service)) {
                    if (!current.targetForeground || !current.complete || !current.attendancePage()
                            || current.dialogMessages > 0) {
                        finish("attendance-header-context-changed"); return;
                    }
                    normalizeHeader(current);
                } catch (RuntimeException ignored) { finish("attendance-header-read-failed"); }
            }, 2500);
        }
        private void normalizeHeader(Window window) {
            if (!"JEF-AN00".equals(Build.MODEL) || Build.VERSION.SDK_INT != 31) {
                finish("layout-acceptance-required"); return;
            }
            WindowManager windows = (WindowManager)service.getSystemService(Context.WINDOW_SERVICE);
            if (windows == null) { finish("display-unavailable"); return; }
            DisplayMetrics metrics = new DisplayMetrics(); windows.getDefaultDisplay().getRealMetrics(metrics);
            if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0
                    || Math.abs(metrics.widthPixels / (double)metrics.heightPixels - 1080.0 / 2400.0) > 0.005) {
                finish("layout-acceptance-required"); return;
            }
            float x1 = 150f * metrics.widthPixels / 1080f, x2 = 950f * metrics.widthPixels / 1080f;
            float y = 625f * metrics.heightPixels / 2400f;
            if (!window.rootBounds.contains((int)x1, (int)y) || !window.rootBounds.contains((int)x2, (int)y)) {
                finish("attendance-header-bounds-rejected"); return;
            }
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            String blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            normalizationRequested = true; // Exactly one reference-profile swipe.
            Path path = new Path(); path.moveTo(x1, y); path.lineTo(x2, y);
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 800)).build();
            boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    if (finished || CANCEL_EPOCH.get() != epoch) return;
                    handler.postDelayed(() -> {
                        if (finished || CANCEL_EPOCH.get() != epoch) return;
                        if (SystemClock.elapsedRealtime() > deadline) {
                            finish(timeoutCode()); return;
                        }
                        String check = guard(service);
                        if (check != null) { finish(check); return; }
                        try (Window current = Window.read(service)) {
                            if (!current.targetForeground || !current.complete || !current.attendancePage()
                                    || current.dialogMessages > 0) {
                                finish("attendance-header-context-changed"); return;
                            }
                            normalizationComplete = true;
                            finish("attendance-page-ready");
                        } catch (RuntimeException ignored) { finish("attendance-header-read-failed"); }
                    }, 1000);
                }
                @Override public void onCancelled(GestureDescription description) {
                    if (finished || CANCEL_EPOCH.get() != epoch) return;
                    finish("attendance-header-gesture-cancelled");
                }
            }, handler);
            if (!accepted) finish("attendance-header-gesture-rejected");
            else handler.postDelayed(() -> {
                if (!finished && CANCEL_EPOCH.get() == epoch && !normalizationComplete)
                    finish("attendance-header-gesture-timeout");
            }, 3000);
        }
        private void openLauncherIcon(Window window) {
            AccessibilityNodeInfo icon = window.launcherIcons.get(0);
            Rect expected = new Rect(); icon.getBoundsInScreen(expected);
            if (!validActionBounds(service, expected, window.rootBounds)
                    || expected.width() > window.rootBounds.width() * 0.35
                    || expected.height() > window.rootBounds.height() * 0.2) {
                finish("dingtalk-launcher-icon-bounds-rejected"); return;
            }
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            launcherIconRequested = true; // Consume before the single icon input.
            if (!icon.refresh() || !window.rootPackage.equals(text(icon.getPackageName()))
                    || !"钉钉".equals(text(icon.getText())) || !icon.isVisibleToUser() || !icon.isEnabled()) {
                finish("dingtalk-launcher-icon-changed"); return;
            }
            Rect current = new Rect(); icon.getBoundsInScreen(current);
            if (!expected.equals(current)) { finish("dingtalk-launcher-icon-changed"); return; }
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            String blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            // Never climb to the full-screen launcher parent or click an ancestor.
            if (icon.performAction(AccessibilityNodeInfo.ACTION_CLICK)) { again(); return; }
            if (CANCEL_EPOCH.get() != epoch) { cancelNow(); return; }
            blocked = guard(service);
            if (blocked != null) { finish(blocked); return; }
            Path path = new Path(); path.moveTo(expected.exactCenterX(), expected.exactCenterY());
            GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
            boolean accepted = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                @Override public void onCompleted(GestureDescription description) {
                    if (finished || CANCEL_EPOCH.get() != epoch) return;
                    again();
                }
                @Override public void onCancelled(GestureDescription description) {
                    if (finished || CANCEL_EPOCH.get() != epoch) return;
                    finish("dingtalk-launcher-gesture-cancelled");
                }
            }, handler);
            if (!accepted) finish("dingtalk-launcher-gesture-rejected");
            else handler.postDelayed(() -> {
                if (!finished && CANCEL_EPOCH.get() == epoch) run();
            }, 2000);
        }
        private void again() { if (!finished) handler.postDelayed(this, POLL_MS); }
        private void finish(String code) {
            if (finished) return;
            finished = true; handler.removeCallbacksAndMessages(null);
            if (activeNavigation == this) activeNavigation = null;
            if (CANCEL_EPOCH.get() == epoch) callback.onResult(code);
        }
        private void cancelNow() {
            if (finished) return;
            finished = true; handler.removeCallbacksAndMessages(null);
            if (activeNavigation == this) activeNavigation = null;
        }
    }

    private static boolean click(AccessibilityNodeInfo label, Rect windowBounds) {
        AccessibilityNodeInfo target = clickable(label, windowBounds);
        if (target == null) return false;
        try { return target.performAction(AccessibilityNodeInfo.ACTION_CLICK); }
        finally { target.recycle(); }
    }

    /** Returns a caller-owned click target without dispatching an input. */
    private static AccessibilityNodeInfo clickable(AccessibilityNodeInfo label, Rect windowBounds) {
        AccessibilityNodeInfo node = AccessibilityNodeInfo.obtain(label);
        try {
            for (int depth = 0; node != null && depth < 3; depth++) {
                Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                if (!DING_PACKAGE.equals(text(node.getPackageName())) || !node.isVisibleToUser()
                        || !node.isEnabled() || node.isPassword() || bounds.isEmpty()
                        || !windowBounds.contains(bounds)) return null;
                if (node.isClickable()) {
                    // An entire screen is never an acceptable clickable ancestor.
                    if (bounds.width() >= windowBounds.width() * 0.9
                            && bounds.height() >= windowBounds.height() * 0.3) return null;
                    AccessibilityNodeInfo result = node; node = null; return result;
                }
                AccessibilityNodeInfo parent = node.getParent(); node.recycle(); node = parent;
            }
            return null;
        } finally { if (node != null) node.recycle(); }
    }

    private static final class Window implements AutoCloseable {
        final List<AccessibilityNodeInfo> owned = new ArrayList<>();
        final List<AccessibilityNodeInfo> shortcuts = new ArrayList<>();
        final List<AccessibilityNodeInfo> checkoutLabels = new ArrayList<>();
        final List<AccessibilityNodeInfo> updateLabels = new ArrayList<>();
        final List<AccessibilityNodeInfo> launcherIcons = new ArrayList<>();
        final List<AccessibilityNodeInfo> launcherWorkspaces = new ArrayList<>();
        final List<AccessibilityNodeInfo> expiredConfirm = new ArrayList<>();
        final List<AccessibilityNodeInfo> earlyLabels = new ArrayList<>();
        final List<AccessibilityNodeInfo> statsTitles = new ArrayList<>();
        final List<Rect> webViewBounds = new ArrayList<>();
        final Rect rootBounds = new Rect();
        String rootPackage;
        boolean targetForeground, launcherForeground, complete;
        int punchTabs, statsTabs, settingsTabs, dialogMessages, expiredMessages, passwordNodes, windowId;
        int notificationBoards, notificationContainers, notificationBacks, editableNodes;

        static Window read(AccessibilityService service) {
            Window result = new Window();
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            try {
                AccessibilityNodeInfo root = service.getRootInActiveWindow();
                if (root == null) return result;
                result.owned.add(root); root.getBoundsInScreen(result.rootBounds);
                result.windowId = root.getWindowId();
                result.rootPackage = text(root.getPackageName());
                result.targetForeground = DING_PACKAGE.equals(result.rootPackage);
                result.launcherForeground = "com.huawei.android.launcher".equals(result.rootPackage)
                    || "com.android.launcher3".equals(result.rootPackage)
                    || "com.android.launcher".equals(result.rootPackage);
                if (!result.targetForeground && !result.launcherForeground) return result;
                queue.add(root);
                int count = 0;
                while (!queue.isEmpty() && count < NODE_LIMIT) {
                    AccessibilityNodeInfo node = queue.remove(); count++;
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                    if (result.targetForeground && DING_PACKAGE.equals(text(node.getPackageName()))
                            && node.isVisibleToUser() && node.isEnabled() && !bounds.isEmpty()) {
                        if (node.isPassword()) result.passwordNodes++;
                        if (node.isEditable()) result.editableNodes++;
                        String webClass = text(node.getClassName());
                        if ("android.webkit.WebView".equals(webClass) || "com.uc.webview.export.WebView".equals(webClass))
                            result.webViewBounds.add(new Rect(bounds));
                    }
                    if (result.launcherForeground && result.rootPackage.equals(text(node.getPackageName()))
                            && node.isVisibleToUser() && node.isEnabled() && !node.isPassword()
                            && !bounds.isEmpty() && "钉钉".equals(text(node.getText()))
                            && "android.widget.TextView".equals(text(node.getClassName()))) result.launcherIcons.add(node);
                    if (result.launcherForeground && result.rootPackage.equals(text(node.getPackageName()))
                            && node.isVisibleToUser() && node.isEnabled() && !node.isPassword()
                            && "com.huawei.android.launcher:id/workspace".equals(text(node.getViewIdResourceName()))
                            && "android.widget.ScrollView".equals(text(node.getClassName()))) result.launcherWorkspaces.add(node);
                    if (DING_PACKAGE.equals(text(node.getPackageName())) && node.isVisibleToUser()
                            && node.isEnabled() && !node.isPassword() && !bounds.isEmpty()) {
                        String id = text(node.getViewIdResourceName()), label = text(node.getText());
                        if ((DING_PACKAGE + ":id/layout_chat_mainborad").equals(id)) result.notificationBoards++;
                        if ((DING_PACKAGE + ":id/rl_chat_container").equals(id)) result.notificationContainers++;
                        if ((DING_PACKAGE + ":id/img_back").equals(id)) result.notificationBacks++;
                        String description = text(node.getContentDescription());
                        if ("android:id/message".equals(id)) {
                            result.dialogMessages++;
                            if (EXPIRED_MESSAGE.equals(label)) result.expiredMessages++;
                        }
                        if ("android:id/button1".equals(id) && "确定".equals(label)) result.expiredConfirm.add(node);
                        if (SHORTCUT_ID.equals(id) && "打卡".equals(label)) result.shortcuts.add(node);
                        if ("下班打卡".equals(label) || "下班打卡".equals(description)) result.checkoutLabels.add(node);
                        if ("更新打卡".equals(label) || "更新打卡".equals(description)) result.updateLabels.add(node);
                        if ("早退打卡".equals(label) || "早退打卡".equals(description)) result.earlyLabels.add(node);
                        if ((DING_PACKAGE + ":id/title").equals(id) && "统计".equals(label)) result.statsTitles.add(node);
                        if (TAB_ID.equals(id)) {
                            if ("打卡".equals(label)) result.punchTabs++;
                            else if ("统计".equals(label)) result.statsTabs++;
                            else if ("设置".equals(label)) result.settingsTabs++;
                        }
                    }
                    for (int index = 0; index < node.getChildCount(); index++) {
                        if (result.owned.size() >= NODE_LIMIT) { result.complete = false; return result; }
                        AccessibilityNodeInfo child = node.getChild(index);
                        if (child != null) { result.owned.add(child); queue.add(child); }
                    }
                }
                result.complete = queue.isEmpty();
                return result;
            } catch (RuntimeException failure) { result.close(); throw failure; }
        }
        boolean attendancePage() { return punchTabs == 1 && statsTabs == 1 && settingsTabs == 1; }
        boolean readOnlyNotificationPage() {
            return targetForeground && complete && notificationBoards == 1 && notificationContainers == 1 &&
                notificationBacks == 1 && passwordNodes == 0 && editableNodes == 0 &&
                dialogMessages == 0 && webViewBounds.isEmpty();
        }
        @Override public void close() { for (AccessibilityNodeInfo node : owned) node.recycle(); owned.clear(); }
    }

    private static String text(CharSequence value) { return value == null ? "" : value.toString().trim(); }
}

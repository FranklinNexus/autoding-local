package org.autoding.research;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.app.KeyguardManager;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/** A single local PIN attempt, restricted to a verified SystemUI keypad. */
public final class NativeUnlock {
    public interface Callback { void onResult(boolean success, String code); }
    private static boolean busy;
    private static Attempt current;
    private static final AtomicLong cancellations = new AtomicLong();
    private NativeUnlock() {}

    static void start(ProbeAccessibilityService service, Callback callback) {
        if (busy) { deliver(callback, false, "unlock-busy"); return; }
        busy = true;
        try { current = new Attempt(service, callback); current.start(); }
        catch (RuntimeException ignored) { busy = false; deliver(callback, false, "unlock-setup-failed"); }
    }
    public static void cancel() { cancel(null); }
    static long cancellationVersion() { return cancellations.get(); }
    static void cancel(ProbeAccessibilityService service) {
        cancellations.incrementAndGet();
        Runnable work = () -> {
            Attempt attempt = current;
            if (attempt != null && (service == null || attempt.service == service)) attempt.finish(false, "unlock-cancelled");
        };
        if (Looper.myLooper() == Looper.getMainLooper()) work.run();
        else new Handler(Looper.getMainLooper()).post(work);
    }
    private static void deliver(Callback callback, boolean success, String code) {
        try { callback.onResult(success, code); } catch (RuntimeException ignored) {}
    }
    private static final class Attempt {
        final ProbeAccessibilityService service;
        final Callback callback;
        final Handler handler = new Handler(Looper.getMainLooper());
        final KeyguardManager keyguard;
        final long started = SystemClock.elapsedRealtime();
        PowerManager.WakeLock wake;
        char[] pin;
        int position, acknowledged;
        long acknowledgementDeadline;
        boolean finished, consumed;
        String expectedPackage, expectedEntry;
        int expectedWindow;
        Attempt(ProbeAccessibilityService service, Callback callback) {
            this.service = service; this.callback = callback;
            keyguard = (KeyguardManager)service.getSystemService(AccessibilityService.KEYGUARD_SERVICE);
        }
        void start() {
            try {
                if (keyguard == null) { finish(false, "keyguard-unavailable"); return; }
                if (!keyguard.isKeyguardLocked()) { finish(true, "already-unlocked"); return; }
                if (!keyguard.isDeviceSecure()) { finish(false, "secure-keyguard-not-confirmed"); return; }
                if (PinStore.isAttemptBlocked(service)) { finish(false, "pin-attempt-blocked"); return; }
                if (!PinStore.isSaved(service)) { finish(false, "pin-not-saved"); return; }
                PowerManager power = (PowerManager)service.getSystemService(AccessibilityService.POWER_SERVICE);
                wake = power.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "AutoDingResearch:nativeUnlock");
                wake.acquire(20000);
                handler.postDelayed(() -> finish(false, "unlock-timeout"), 18000);
                later(this::beforeHome, 350);
            } catch (Exception ignored) { finish(false, "unlock-setup-failed"); }
        }
        boolean continuing() {
            if (finished) return false;
            if (ProbeAccessibilityService.active != service) { finish(false, "service-disconnected"); return false; }
            if (SystemClock.elapsedRealtime() - started > 18000) { finish(false, "unlock-timeout"); return false; }
            if (!keyguard.isKeyguardLocked()) { finish(true, consumed ? "verified-unlocked" : "unlocked-before-pin"); return false; }
            if (!keyguard.isDeviceSecure()) { finish(false, "keyguard-state-changed"); return false; }
            return true;
        }
        void beforeHome() {
            if (!continuing()) return;
            try (Keypad keys = inspect(service, true)) {
                if (keys.valid()) { stableCheck(keys); return; }
            } catch (Exception ignored) { finish(false, "pin-ui-read-failed"); return; }
            if (!service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
                finish(false, "home-action-rejected"); return;
            }
            later(this::beforeSwipe, 450);
        }
        void beforeSwipe() {
            if (!continuing()) return;
            try (Keypad keys = inspect(service, true)) {
                if (keys.valid()) { stableCheck(keys); return; }
            } catch (Exception ignored) { finish(false, "pin-ui-read-failed"); return; }
            try {
                DisplayMetrics screen = service.getResources().getDisplayMetrics();
                if (screen.widthPixels <= 0 || screen.heightPixels <= 0) { finish(false, "display-unavailable"); return; }
                Path path = new Path();
                path.moveTo(screen.widthPixels * .5f, screen.heightPixels * .82f);
                path.lineTo(screen.widthPixels * .5f, screen.heightPixels * .2f);
                GestureDescription gesture = new GestureDescription.Builder()
                    .addStroke(new GestureDescription.StrokeDescription(path, 0, 350)).build();
                boolean sent = service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription gesture) { later(() -> awaitKeypad(0), 600); }
                    @Override public void onCancelled(GestureDescription gesture) { finish(false, "pin-reveal-cancelled"); }
                }, handler);
                if (!sent) finish(false, "pin-reveal-rejected");
            } catch (Exception ignored) { finish(false, "pin-reveal-failed"); }
        }
        void awaitKeypad(int retry) {
            if (!continuing()) return;
            try (Keypad keys = inspect(service, true)) {
                if (keys.valid()) { stableCheck(keys); return; }
                if (retry >= 3) { finish(false, keys.code); return; }
            } catch (Exception ignored) { finish(false, "pin-ui-read-failed"); return; }
            later(() -> awaitKeypad(retry + 1), 300);
        }
        void stableCheck(Keypad keys) {
            expectedPackage = keys.pkg; expectedEntry = keys.entryId; expectedWindow = keys.window;
            later(this::beginInput, 250);
        }
        void beginInput() {
            if (!continuing()) return;
            try (Keypad keys = inspect(service, true)) {
                if (!matches(keys)) { finish(false, keys.valid() ? "pin-window-changed" : keys.code); return; }
                pin = PinStore.read(service);
                // Persist before the first click: process death never grants another attempt.
                if (!PinStore.beginAttempt(service)) { finish(false, "pin-attempt-blocked"); return; }
                consumed = true;
            } catch (Exception ignored) { finish(false, "pin-read-failed"); return; }
            clickNext();
        }
        boolean matches(Keypad keys) {
            return keys.valid() && keys.window == expectedWindow && expectedPackage.equals(keys.pkg) && expectedEntry.equals(keys.entryId);
        }
        void clickNext() {
            if (!continuing()) return;
            try (Keypad keys = inspect(service, position == 0)) {
                if (!matches(keys)) { finish(false, keys.valid() ? "pin-window-changed" : keys.code); return; }
                if (keys.inputLength != position) { finish(false, "pin-input-state-changed"); return; }
                AccessibilityNodeInfo key = keys.digits[pin[position] - '0'];
                if (!keyguard.isKeyguardLocked()) { finish(true, "verified-unlocked"); return; }
                if (!key.refresh() || !clickable(key, keys.pkg, expectedWindow) ||
                    digit(key, keys.pkg) != pin[position] - '0' ||
                    !key.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    finish(false, "pin-key-click-failed"); return;
                }
                position++;
                if (position == pin.length) {
                    Arrays.fill(pin, '\0'); pin = null;
                    later(() -> verify(0), 400);
                } else {
                    acknowledgementDeadline = SystemClock.elapsedRealtime() + 1500;
                    later(this::awaitAcknowledgement, 100);
                }
            } catch (Exception ignored) { finish(false, "pin-input-failed"); }
        }
        void awaitAcknowledgement() {
            if (!continuing()) return;
            try (Keypad keys = inspect(service, false)) {
                if (!matches(keys)) { finish(false, keys.valid() ? "pin-window-changed" : keys.code); return; }
                if (keys.inputLength == position) {
                    acknowledged = position;
                    // Recheck the acknowledged state before issuing the next click.
                    later(this::clickNext, 120);
                } else if (keys.inputLength == acknowledged) {
                    if (SystemClock.elapsedRealtime() >= acknowledgementDeadline) finish(false, "pin-progress-unconfirmed");
                    else later(this::awaitAcknowledgement, 100);
                } else {
                    finish(false, "pin-input-state-changed");
                }
            } catch (Exception ignored) { finish(false, "pin-ui-read-failed"); }
        }
        void verify(int retry) {
            if (!continuing()) return;
            if (retry >= 12) { finish(false, "pin-not-verified"); return; }
            later(() -> verify(retry + 1), 350);
        }
        void later(Runnable work, long delay) {
            if (finished) return;
            handler.postDelayed(() -> {
                if (finished) return;
                try { work.run(); } catch (Exception ignored) { finish(false, "unlock-operation-failed"); }
            }, delay);
        }
        void finish(boolean success, String code) {
            if (finished) return;
            finished = true;
            handler.removeCallbacksAndMessages(null);
            if (pin != null) { Arrays.fill(pin, '\0'); pin = null; }
            if (success && consumed) PinStore.resetAttempt(service);
            if (wake != null) try { if (wake.isHeld()) wake.release(); } catch (RuntimeException ignored) {}
            if (current == this) current = null;
            busy = false;
            deliver(callback, success, code);
        }
    }
    private static final class Visit {
        final AccessibilityNodeInfo node;
        final int parent, depth;
        Visit(AccessibilityNodeInfo node, int parent, int depth) { this.node = node; this.parent = parent; this.depth = depth; }
    }
    private static final class Keypad implements AutoCloseable {
        final ArrayList<AccessibilityNodeInfo> nodes = new ArrayList<>();
        final ArrayList<Integer> parents = new ArrayList<>();
        final AccessibilityNodeInfo[] digits = new AccessibilityNodeInfo[10];
        final int[] digitIndices = new int[10];
        String pkg = "", entryId = "", code = "pin-ui-not-recognized";
        int window, entryIndex = -1, inputLength = -1;
        boolean valid() { return "pin-ui-verified".equals(code); }
        @Override public void close() { for (AccessibilityNodeInfo node : nodes) node.recycle(); nodes.clear(); }
    }
    private static boolean system(String pkg) { return "com.android.systemui".equals(pkg) || "com.huawei.systemui".equals(pkg); }
    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
    private static boolean visible(AccessibilityNodeInfo node, String pkg, int window) {
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        return pkg.equals(text(node.getPackageName())) && node.getWindowId() == window &&
            node.isVisibleToUser() && node.isEnabled() && !bounds.isEmpty();
    }
    private static boolean clickable(AccessibilityNodeInfo node, String pkg, int window) {
        return visible(node, pkg, window) && node.isClickable() && (node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0;
    }
    private static int digit(AccessibilityNodeInfo node, String pkg) {
        String id = text(node.getViewIdResourceName());
        return id.matches(java.util.regex.Pattern.quote(pkg) + ":id/(?:key|keyguard_key_?)[0-9]") ? id.charAt(id.length() - 1) - '0' : -1;
    }
    private static boolean ancestor(Keypad keys, int parent, int child) {
        for (int at = child; at >= 0; at = keys.parents.get(at)) if (at == parent) return true;
        return false;
    }
    private static Keypad inspect(ProbeAccessibilityService service, boolean requireEmpty) throws Exception {
        Keypad keys = new Keypad();
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        try {
            AccessibilityNodeInfo root = service.getRootInActiveWindow();
            if (root == null) { keys.code = "pin-window-unavailable"; return keys; }
            keys.pkg = text(root.getPackageName()); keys.window = root.getWindowId();
            pending.add(new Visit(root, -1, 0));
            while (!pending.isEmpty()) {
                Visit visit = pending.remove();
                int index = keys.nodes.size(); keys.nodes.add(visit.node); keys.parents.add(visit.parent);
                if (index >= 1024 || visit.depth > 64) { keys.code = "pin-ui-too-large"; return keys; }
                for (int n = 0; n < visit.node.getChildCount(); n++) {
                    AccessibilityNodeInfo child = visit.node.getChild(n);
                    if (child != null) pending.add(new Visit(child, index, visit.depth + 1));
                    if (pending.size() > 1024) { keys.code = "pin-ui-too-large"; return keys; }
                }
            }
            if (!system(keys.pkg) || keys.window < 0) { keys.code = "pin-system-window-required"; return keys; }
            for (int index = 0; index < keys.nodes.size(); index++) {
                AccessibilityNodeInfo node = keys.nodes.get(index);
                if (!visible(node, keys.pkg, keys.window)) continue;
                String id = text(node.getViewIdResourceName());
                if (id.matches(java.util.regex.Pattern.quote(keys.pkg) + ":id/(fixedPinEntry|pinEntry|passwordEntry|pin_entry|password_entry)")) {
                    if (keys.entryIndex >= 0) { keys.code = "pin-entry-ambiguous"; return keys; }
                    // Root/child lookups can return cached state after an ACTION_CLICK.
                    // Refresh this precise password node before using its progress.
                    if (!node.refresh()) { keys.code = "pin-entry-stale"; return keys; }
                    if (!visible(node, keys.pkg, keys.window) || !id.equals(text(node.getViewIdResourceName()))) {
                        keys.code = "pin-entry-state-changed"; return keys;
                    }
                    if (!node.isPassword() || !node.isFocused()) { keys.code = "pin-entry-not-ready"; return keys; }
                    CharSequence value = node.getText();
                    if (requireEmpty && (value == null || value.length() != 0)) { keys.code = "pin-empty-unverified"; return keys; }
                    keys.inputLength = value == null ? -1 : value.length();
                    keys.entryIndex = index; keys.entryId = id;
                }
                int digit = digit(node, keys.pkg);
                if (digit >= 0) {
                    if (keys.digits[digit] != null || !clickable(node, keys.pkg, keys.window)) {
                        keys.code = "pin-keypad-ambiguous"; return keys;
                    }
                    String label = text(node.getText()), description = text(node.getContentDescription());
                    if ((label.matches("[0-9]") && label.charAt(0) - '0' != digit) ||
                        (description.matches("[0-9]") && description.charAt(0) - '0' != digit)) {
                        keys.code = "pin-key-label-mismatch"; return keys;
                    }
                    keys.digits[digit] = node; keys.digitIndices[digit] = index;
                }
            }
            if (keys.entryIndex < 0) { keys.code = "pin-entry-unavailable"; return keys; }
            for (AccessibilityNodeInfo node : keys.digits) if (node == null) { keys.code = "pin-keypad-incomplete"; return keys; }
            int common = keys.entryIndex;
            while (common >= 0) {
                boolean all = true;
                for (int digit : keys.digitIndices) all &= ancestor(keys, common, digit);
                if (all) break;
                common = keys.parents.get(common);
            }
            if (common < 0 || !visible(keys.nodes.get(common), keys.pkg, keys.window)) {
                keys.code = "pin-keypad-structure-invalid"; return keys;
            }
            Rect entry = new Rect(); keys.nodes.get(keys.entryIndex).getBoundsInScreen(entry);
            for (int n = 0; n < 10; n++) {
                Rect bounds = new Rect(); keys.digits[n].getBoundsInScreen(bounds);
                if (Rect.intersects(entry, bounds)) { keys.code = "pin-keypad-geometry-invalid"; return keys; }
                for (int p = 0; p < n; p++) {
                    Rect other = new Rect(); keys.digits[p].getBoundsInScreen(other);
                    if (Rect.intersects(bounds, other)) { keys.code = "pin-keypad-geometry-invalid"; return keys; }
                }
            }
            keys.code = "pin-ui-verified";
            return keys;
        } catch (Exception failure) { keys.close(); throw failure; }
        finally { while (!pending.isEmpty()) pending.remove().node.recycle(); }
    }
}

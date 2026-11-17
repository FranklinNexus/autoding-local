package org.autoding.research;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ColorSpace;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.os.SystemClock;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;

/** Explicit scans and locally configured native actions; images remain in memory. */
public final class ProbeAccessibilityService extends AccessibilityService {
    static volatile ProbeAccessibilityService active;
    static volatile long layoutVersion;
    public static volatile long contentVersion;
    @Override protected void onServiceConnected() { active = this; ProbeState.put(this, "serviceConnectedAt", System.currentTimeMillis()); }
    @Override public void onDestroy() {
        if (active == this) active = null;
        NativeUnlock.cancel(this);
        super.onDestroy();
    }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event != null && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED)) layoutVersion++;
        if (event != null && (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
            event.getEventType() == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
            event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)) contentVersion++;
    }
    @Override public void onInterrupt() {}
    public static void requestNativeUnlock(Context context, NativeUnlock.Callback callback) {
        if (callback == null) throw new IllegalArgumentException("callback-required");
        final long generation = NativeUnlock.cancellationVersion();
        Runnable request = () -> {
            if (generation != NativeUnlock.cancellationVersion()) { callback.onResult(false, "unlock-cancelled"); return; }
            ProbeAccessibilityService service = active;
            if (service == null) callback.onResult(false, "service-not-connected");
            else NativeUnlock.start(service, callback);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) request.run();
        else new Handler(Looper.getMainLooper()).post(request);
    }
    public interface FrameCallback { void onFrame(Bitmap bitmap, long capturedAt, String code); }
    public static void captureFrame(Context context, FrameCallback callback) {
        if (callback == null) throw new IllegalArgumentException("callback-required");
        new Handler(Looper.getMainLooper()).post(() -> {
            ProbeAccessibilityService service = active;
            if (service == null) { callback.onFrame(null, System.currentTimeMillis(), "service-not-connected"); return; }
            try {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(), new TakeScreenshotCallback() {
                    @Override public void onSuccess(ScreenshotResult screenshot) {
                        Bitmap hardware = null, software = null;
                        HardwareBuffer buffer = screenshot.getHardwareBuffer();
                        long capturedAt = System.currentTimeMillis();
                        try {
                            ColorSpace color = screenshot.getColorSpace();
                            hardware = Bitmap.wrapHardwareBuffer(buffer, color);
                            if (hardware != null) software = hardware.copy(Bitmap.Config.ARGB_8888, false);
                        } catch (RuntimeException ignored) { /* Callback contains only a stable code. */ }
                        finally {
                            if (hardware != null) hardware.recycle();
                            buffer.close();
                        }
                        callback.onFrame(software, capturedAt, software == null ? "capture-copy-failed" : "frame-available");
                    }
                    @Override public void onFailure(int errorCode) {
                        callback.onFrame(null, System.currentTimeMillis(), "capture-rejected");
                    }
                });
            } catch (RuntimeException ignored) { callback.onFrame(null, System.currentTimeMillis(), "capture-call-failed"); }
        });
    }
    static final class ScanRequest {
        final String id;
        final boolean accepted;
        ScanRequest(String id, boolean accepted) { this.id = id; this.accepted = accepted; }
    }
    static ScanRequest requestScan(Context context) {
        ProbeAccessibilityService service = active;
        String scanId = UUID.randomUUID().toString();
        long requestedAt = System.currentTimeMillis();
        boolean accepted = service != null;
        ProbeState.beginScan(context, scanId, requestedAt, accepted);
        if (!accepted) {
            writeError(context, scanId, requestedAt, "scanError", "service-not-connected");
            return new ScanRequest(scanId, false);
        }
        try {
            service.getMainExecutor().execute(() -> service.scan(scanId, requestedAt));
            return new ScanRequest(scanId, true);
        } catch (RuntimeException ignored) {
            ProbeState.putScan(context, scanId, "scanRequested", false);
            writeError(context, scanId, requestedAt, "scanError", "scan-dispatch-failed");
            return new ScanRequest(scanId, false);
        }
    }
    private static JSONObject evidence(String scanId, long requestedAt) throws Exception {
        JSONObject result = new JSONObject();
        result.put("scanId", scanId);
        result.put("requestedAt", requestedAt);
        result.put("completedAt", System.currentTimeMillis());
        result.put("completedElapsedRealtime", SystemClock.elapsedRealtime());
        return result;
    }
    private static void writeError(Context context, String scanId, long requestedAt, String key, String code) {
        try {
            JSONObject result = evidence(scanId, requestedAt);
            result.put("outcome", code);
            ProbeState.putScan(context, scanId, key, result);
        } catch (Exception ignored) {}
    }
    private void scan(String scanId, long requestedAt) {
        JSONObject observation = new JSONObject();
        ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
        Set<String> digits = new HashSet<>();
        int count = 0, emptyFocusedPin = 0, pinKnownEmpty = 0, pinNullText = 0, attendanceTabs = 0, punchLabels = 0;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            observation.put("rootAvailable", root != null);
            if (root != null) queue.add(root);
            while (!queue.isEmpty() && count < 1024) {
                AccessibilityNodeInfo node = queue.remove();
                try {
                    count++;
                    String pkg = text(node.getPackageName()), id = text(node.getViewIdResourceName());
                    CharSequence rawText = node.getText();
                    boolean inputEmpty = rawText == null || rawText.length() == 0;
                    String value = node.isPassword() ? "" : text(rawText);
                    String desc = node.isPassword() ? "" : text(node.getContentDescription());
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                    boolean visible = node.isVisibleToUser() && node.isEnabled() && !bounds.isEmpty();
                    boolean system = pkg.equals("com.android.systemui") || pkg.equals("com.huawei.systemui");
                    if (system && visible) {
                        if (id.matches(".*:id/(fixedPinEntry|pinEntry|passwordEntry|pin_entry|password_entry)") &&
                            node.isPassword() && node.isFocused() && inputEmpty) {
                            emptyFocusedPin++;
                            if (node.getText() == null) pinNullText++; else pinKnownEmpty++;
                        }
                        if (value.matches("[0-9]")) digits.add(value);
                        if (desc.matches("[0-9]")) digits.add(desc);
                        if (id.matches(".*:id/(?:key|keyguard_key_?)[0-9]")) digits.add(id.substring(id.length() - 1));
                    }
                    if (pkg.equals("com.alibaba.android.rimet") && visible && !node.isPassword()) {
                        if (id.endsWith(":id/h5_tabbaritem_txticon") && (value.equals("打卡") || value.equals("统计") || value.equals("设置"))) attendanceTabs++;
                        if (value.matches(".*(?:上班|下班|已打卡).*")) punchLabels++;
                    }
                    for (int n = 0; n < node.getChildCount() && queue.size() < 1024; n++) {
                        AccessibilityNodeInfo child = node.getChild(n);
                        if (child != null) queue.add(child);
                    }
                } finally { node.recycle(); }
            }
            observation.put("scannedNodes", count);
            observation.put("scanComplete", queue.isEmpty());
            observation.put("emptyFocusedPinEntries", emptyFocusedPin);
            observation.put("pinKnownEmptyCount", pinKnownEmpty);
            observation.put("pinNullTextCount", pinNullText);
            observation.put("digitCount", digits.size());
            observation.put("pinUiCandidate", emptyFocusedPin == 1 && digits.size() == 10);
            observation.put("attendanceTabCount", attendanceTabs);
            observation.put("punchLabelCount", punchLabels);
            observation.put("locked", ((KeyguardManager)getSystemService(KEYGUARD_SERVICE)).isKeyguardLocked());
            observation.put("scanId", scanId);
            observation.put("requestedAt", requestedAt);
            observation.put("completedAt", System.currentTimeMillis());
            observation.put("completedElapsedRealtime", SystemClock.elapsedRealtime());
            ProbeState.putScan(this, scanId, "windowScan", observation);
        } catch (Exception ignored) { writeError(this, scanId, requestedAt, "scanError", "window-read-failed"); }
        finally { while (!queue.isEmpty()) queue.remove().recycle(); }
        try {
            takeScreenshot(Display.DEFAULT_DISPLAY, getMainExecutor(), new TakeScreenshotCallback() {
                @Override public void onSuccess(ScreenshotResult screenshot) {
                    HardwareBuffer buffer = screenshot.getHardwareBuffer();
                    try {
                        JSONObject result = evidence(scanId, requestedAt);
                        result.put("outcome", "available");
                        result.put("width", buffer.getWidth()); result.put("height", buffer.getHeight());
                        result.put("captureTimestamp", screenshot.getTimestamp());
                        result.put("imagePersisted", false);
                        ProbeState.putScan(ProbeAccessibilityService.this, scanId, "screenshot", result);
                    } catch (Exception ignored) {}
                    finally { buffer.close(); }
                }
                @Override public void onFailure(int errorCode) {
                    try {
                        JSONObject result = evidence(scanId, requestedAt);
                        result.put("outcome", "rejected"); result.put("errorCode", errorCode); result.put("imagePersisted", false);
                        ProbeState.putScan(ProbeAccessibilityService.this, scanId, "screenshot", result);
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception ignored) { writeError(this, scanId, requestedAt, "screenshotError", "capture-call-failed"); }
    }
    private static String text(CharSequence value) { return value == null ? "" : value.toString(); }
}

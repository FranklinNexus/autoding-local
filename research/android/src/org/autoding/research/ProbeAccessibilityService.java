package org.autoding.research;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.Display;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.json.JSONObject;

/** Explicit, aggregate-only scans. No clicks, PIN entry or saved screenshots. */
public final class ProbeAccessibilityService extends AccessibilityService {
    static volatile ProbeAccessibilityService active;
    @Override protected void onServiceConnected() { active = this; ProbeState.put(this, "serviceConnectedAt", System.currentTimeMillis()); }
    @Override public void onDestroy() { if (active == this) active = null; super.onDestroy(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {}
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
        int count = 0, emptyFocusedPin = 0, attendanceTabs = 0, punchLabels = 0;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            observation.put("rootAvailable", root != null);
            if (root != null) queue.add(root);
            while (!queue.isEmpty() && count < 1024) {
                AccessibilityNodeInfo node = queue.remove();
                try {
                    count++;
                    String pkg = text(node.getPackageName()), id = text(node.getViewIdResourceName());
                    String value = text(node.getText()), desc = text(node.getContentDescription());
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                    boolean visible = node.isVisibleToUser() && node.isEnabled() && !bounds.isEmpty();
                    boolean system = pkg.equals("com.android.systemui") || pkg.equals("com.huawei.systemui");
                    if (system && visible) {
                        if (id.matches(".*:id/(fixedPinEntry|pinEntry|passwordEntry|pin_entry|password_entry)") &&
                            node.isPassword() && node.isFocused() && value.length() == 0) emptyFocusedPin++;
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

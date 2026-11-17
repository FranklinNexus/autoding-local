package org.autoding.research;
public final class ProbeAccessibilityService extends android.accessibilityservice.AccessibilityService {
    public static ProbeAccessibilityService active;
    public final android.app.KeyguardManager keyguard = new android.app.KeyguardManager();
    public final android.os.PowerManager power = new android.os.PowerManager();
    public android.view.accessibility.AccessibilityNodeInfo root, revealed;
    public int homeCount, swipeCount;
    public boolean loseGestureCallback;
    public Object getSystemService(String name) { return KEYGUARD_SERVICE.equals(name) ? keyguard : power; }
    public android.content.res.Resources getResources() { return new android.content.res.Resources(); }
    public android.view.accessibility.AccessibilityNodeInfo getRootInActiveWindow() { return root; }
    public boolean performGlobalAction(int action) { if (action != GLOBAL_ACTION_HOME) throw new AssertionError("unexpected global action"); homeCount++; return true; }
    public boolean dispatchGesture(android.accessibilityservice.GestureDescription gesture, GestureResultCallback callback, android.os.Handler handler) {
        swipeCount++; if (revealed != null) root = revealed;
        if (!loseGestureCallback) callback.onCompleted(gesture); return true;
    }
}

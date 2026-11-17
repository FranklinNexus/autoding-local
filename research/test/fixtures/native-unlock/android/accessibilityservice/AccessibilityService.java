package android.accessibilityservice;
public class AccessibilityService {
    public static final String KEYGUARD_SERVICE = "keyguard", POWER_SERVICE = "power";
    public static final int GLOBAL_ACTION_HOME = 2;
    public static class GestureResultCallback {
        public void onCompleted(GestureDescription gesture) {}
        public void onCancelled(GestureDescription gesture) {}
    }
}

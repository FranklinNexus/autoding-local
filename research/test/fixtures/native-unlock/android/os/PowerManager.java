package android.os;
public final class PowerManager {
    public static final int SCREEN_DIM_WAKE_LOCK = 6, ACQUIRE_CAUSES_WAKEUP = 0x10000000;
    public final WakeLock lock = new WakeLock();
    public WakeLock newWakeLock(int flags, String tag) { return lock; }
    public static final class WakeLock {
        public boolean held;
        public void acquire(long timeout) { held = true; }
        public boolean isHeld() { return held; }
        public void release() { held = false; }
    }
}

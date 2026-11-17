package android.app;
public final class KeyguardManager {
    public boolean locked = true, secure = true;
    public boolean isKeyguardLocked() { return locked; }
    public boolean isDeviceSecure() { return secure; }
}

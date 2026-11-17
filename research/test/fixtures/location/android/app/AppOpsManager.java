package android.app;
public class AppOpsManager {
    public static final String OPSTR_MOCK_LOCATION = "mock";
    public static final int MODE_ALLOWED = 0;
    public int mode = 1;
    public int checkOpNoThrow(String op, int uid, String pkg) {
        if (!OPSTR_MOCK_LOCATION.equals(op)) throw new AssertionError("Unexpected app-op");
        return mode;
    }
}

package android.location;
public class Location {
    private final String provider;
    public static int modernChecks, legacyChecks;
    public static boolean marker = true;
    public Location(String provider) { this.provider = provider; }
    public String getProvider() { return provider; }
    public void setLatitude(double value) {}
    public void setLongitude(double value) {}
    public void setAccuracy(float value) {}
    public void setTime(long value) {}
    public void setElapsedRealtimeNanos(long value) {}
    public boolean isMock() { modernChecks++; return marker; }
    public boolean isFromMockProvider() { legacyChecks++; return marker; }
}

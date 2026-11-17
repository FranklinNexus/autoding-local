package android.location;
import java.util.ArrayList;
import java.util.List;
public class LocationManager {
    public boolean present, failAdd, failInject, failRead, failCleanup, emptyReadback;
    public int adds, injections, reads, removes;
    private void check(String name) {
        if (!"autoding_research".equals(name)) throw new AssertionError("A standard location provider was touched: " + name);
    }
    public List<String> getAllProviders() {
        List<String> names = new ArrayList<>();
        names.add("gps"); names.add("network"); names.add("fused");
        if (present) names.add("autoding_research");
        return names;
    }
    public void addTestProvider(String name, boolean a, boolean b, boolean c, boolean d,
            boolean e, boolean f, boolean g, int power, int accuracy) {
        check(name); adds++;
        if (failAdd) throw new SecurityException("PRIVATE_EXCEPTION_COORDINATE");
        present = true;
    }
    public void setTestProviderEnabled(String name, boolean value) { check(name); }
    public void setTestProviderLocation(String name, Location value) {
        check(name); injections++;
        if (failInject) throw new IllegalStateException("PRIVATE_EXCEPTION_COORDINATE");
    }
    public Location getLastKnownLocation(String name) {
        check(name); reads++;
        if (failRead) throw new SecurityException("PRIVATE_EXCEPTION_COORDINATE");
        return emptyReadback ? null : new Location(name);
    }
    public void removeTestProvider(String name) {
        check(name); removes++;
        if (failCleanup) throw new IllegalStateException("PRIVATE_EXCEPTION_COORDINATE");
        present = false;
    }
}

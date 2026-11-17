package fixture;
import android.content.Context;
import android.location.Location;
import android.os.Build;
import org.autoding.research.LocationProbe;

public final class LocationProbeHarness {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static Context ready() {
        Context context = new Context();
        context.operations.mode = 0;
        Location.marker = true; Location.modernChecks = 0; Location.legacyChecks = 0;
        return context;
    }
    private static void privateOutput(String text) {
        check(!text.contains("PRIVATE_EXCEPTION") && !text.contains("latitude") && !text.contains("longitude")
            && !text.contains("31.0") && !text.contains("121.0"), "Diagnostics exposed coordinates or private exception text");
        check(text.contains("\"readsRealLocation\":false") && text.contains("\"affectsStandardProviders\":false")
            && text.contains("\"provesAttendanceAcceptance\":false"), "Fixture scope was lost");
    }
    public static void main(String[] args) {
        Context c = ready();
        String r = LocationProbe.inspect(c);
        check(r.contains("ready-for-local-fixture"), "Ready inspection failed");
        check(c.locations.adds == 0 && c.locations.reads == 0, "Inspection changed or read location");
        privateOutput(r);
        c = ready(); c.operations.mode = 1;
        r = LocationProbe.runMockFixture(c);
        check(r.contains("select-this-app-as-mock-location-app") && c.locations.adds == 0, "Missing mock selection mutated providers");
        c = ready(); c.permission = 1;
        r = LocationProbe.runMockFixture(c);
        check(r.contains("grant-precise-location-for-fixture-readback") && c.locations.adds == 0, "Missing fine permission mutated providers");
        for (int api : new int[]{23, 31, 35}) {
            Build.VERSION.SDK_INT = api; c = ready();
            r = LocationProbe.runMockFixture(c);
            check(r.contains("mock-marker-observed") && r.contains("\"isMock\":true"), "Mock marker was not reported");
            check(c.locations.adds == 1 && c.locations.injections == 1 && c.locations.reads == 1,
                "Fixture did not inject and read exactly one named provider");
            check(!c.locations.present && c.locations.removes == 1 && r.contains("\"cleanupSucceeded\":true"),
                "Successful fixture leaked its provider");
            check(api >= 31 ? Location.modernChecks == 1 && Location.legacyChecks == 0
                : Location.legacyChecks == 1 && Location.modernChecks == 0, "Wrong mock API branch");
            privateOutput(r);
        }
        c = ready(); c.locations.failInject = true;
        r = LocationProbe.runMockFixture(c);
        check(!c.locations.present && c.locations.removes == 1 && r.contains("test-provider-injection-failed"),
            "Injection failure leaked provider or lost failure stage");
        privateOutput(r);
        c = ready(); c.locations.failRead = true;
        r = LocationProbe.runMockFixture(c);
        check(!c.locations.present && c.locations.removes == 1 && r.contains("mock-or-location-permission-rejected"),
            "Read failure leaked provider");
        privateOutput(r);
        c = ready(); c.locations.failAdd = true;
        r = LocationProbe.runMockFixture(c);
        check(!c.locations.present && r.contains("\"cleanupSucceeded\":true") && !r.contains("cleanup-required"),
            "Rejected creation was misreported as a cleanup leak");
        privateOutput(r);
        c = ready(); c.locations.emptyReadback = true;
        r = LocationProbe.runMockFixture(c);
        check(r.contains("readback-unavailable") && r.contains("\"isMock\":null") && !c.locations.present,
            "Empty readback claimed success");
        c = ready(); Location.marker = false;
        r = LocationProbe.runMockFixture(c);
        check(r.contains("mock-marker-not-observed") && r.contains("\"isMock\":false"), "Unexpected marker was guessed true");
        c = ready(); c.locations.failCleanup = true;
        r = LocationProbe.runMockFixture(c);
        check(r.contains("test-provider-cleanup-required") && c.locations.present, "Cleanup failure was hidden");
        c.locations.failCleanup = false;
        LocationProbe.stop(c);
        check(!c.locations.present && LocationProbe.inspect(c).contains("\"cleanupSucceeded\":true"),
            "Explicit stop did not remove own leftover provider");
        c = ready(); c.locations.present = true;
        r = LocationProbe.runMockFixture(c);
        check(!c.locations.present && c.locations.removes == 2 && r.contains("mock-marker-observed"),
            "A previous process fixture was not replaced and cleaned");
        c = ready(); c.locations = null;
        r = LocationProbe.runMockFixture(c);
        check(r.contains("location-service-unavailable"), "Missing location service was not explicit");
        System.out.println("PASS: " + checks + " isolated location fixture checks");
    }
}

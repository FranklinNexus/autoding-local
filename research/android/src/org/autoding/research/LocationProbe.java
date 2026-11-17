/* SPDX-License-Identifier: GPL-3.0-or-later */
package org.autoding.research;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Process;
import android.os.SystemClock;

/**
 * A local Android capability fixture, never a GPS/network/fused replacement.
 * No real device coordinates, app data or attendance actions are read here.
 */
public final class LocationProbe {
    private static final String PROVIDER = "autoding_research";
    private static boolean lastCleanupSucceeded = true;

    private LocationProbe() {}

    private static boolean mockAppSelected(Context context) {
        try {
            AppOpsManager operations = (AppOpsManager)context.getSystemService(Context.APP_OPS_SERVICE);
            return operations != null && operations.checkOpNoThrow(AppOpsManager.OPSTR_MOCK_LOCATION,
                Process.myUid(), context.getPackageName()) == AppOpsManager.MODE_ALLOWED;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean fineLocationGranted(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static LocationManager manager(Context context) {
        return (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
    }

    private static boolean providerPresent(LocationManager manager) {
        return manager != null && manager.getAllProviders().contains(PROVIDER);
    }

    private static String result(String operation, String outcome, boolean selected, boolean fine,
            boolean injected, boolean readBack, Boolean mock, boolean cleanup, boolean present) {
        // All strings are fixed internal status codes. Never serialize a Location,
        // coordinates, system output or exception messages into diagnostics.
        return "{\"operation\":\"" + operation + "\",\"outcome\":\"" + outcome
            + "\",\"apiLevel\":" + Build.VERSION.SDK_INT
            + ",\"provider\":\"" + PROVIDER + "\",\"mockAppSelected\":" + selected
            + ",\"fineLocationGranted\":" + fine + ",\"injectionRequested\":" + injected
            + ",\"readBack\":" + readBack + ",\"isMock\":" + mock
            + ",\"cleanupSucceeded\":" + cleanup + ",\"testProviderPresent\":" + present
            + ",\"readsRealLocation\":false,\"affectsStandardProviders\":false"
            + ",\"provesAttendanceAcceptance\":false}";
    }

    public static synchronized String inspect(Context context) {
        boolean selected = mockAppSelected(context), fine = fineLocationGranted(context);
        boolean present = false;
        String outcome = !selected ? "select-this-app-as-mock-location-app"
            : !fine ? "grant-precise-location-for-fixture-readback" : "ready-for-local-fixture";
        try {
            LocationManager manager = manager(context);
            if (manager == null) outcome = "location-service-unavailable";
            else present = providerPresent(manager);
        } catch (RuntimeException ignored) {
            outcome = "provider-inventory-unavailable";
        }
        return result("inspect", outcome, selected, fine, false, false, null, lastCleanupSucceeded, present);
    }

    /** Explicit user action only; caller may run this on a worker thread. */
    @SuppressWarnings("deprecation")
    public static synchronized String runMockFixture(Context context) {
        boolean selected = mockAppSelected(context), fine = fineLocationGranted(context);
        if (!selected || !fine) return result("fixture",
            !selected ? "select-this-app-as-mock-location-app" : "grant-precise-location-for-fixture-readback",
            selected, fine, false, false, null, lastCleanupSucceeded, false);

        LocationManager manager = manager(context);
        if (manager == null) return result("fixture", "location-service-unavailable", selected, fine,
            false, false, null, lastCleanupSucceeded, false);
        boolean injected = false, readBack = false, present = false, touched = false;
        Boolean mock = null;
        String outcome = "test-provider-create-failed";
        lastCleanupSucceeded = true;
        try {
            // Remove only our own fixture if an earlier process left it behind.
            if (providerPresent(manager)) manager.removeTestProvider(PROVIDER);
            touched = true;
            manager.addTestProvider(PROVIDER, false, false, false, false, true, true, true,
                Criteria.POWER_LOW, Criteria.ACCURACY_FINE);
            manager.setTestProviderEnabled(PROVIDER, true);
            Location fixture = new Location(PROVIDER);
            fixture.setLatitude(31.0);
            fixture.setLongitude(121.0);
            fixture.setAccuracy(5.0f);
            fixture.setTime(System.currentTimeMillis());
            fixture.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
            outcome = "test-provider-injection-failed";
            manager.setTestProviderLocation(PROVIDER, fixture);
            injected = true;
            outcome = "test-provider-readback-unavailable";
            // Only this nonstandard fixture is queried, never GPS/network/fused.
            Location observed = manager.getLastKnownLocation(PROVIDER);
            if (observed != null && PROVIDER.equals(observed.getProvider())) {
                readBack = true;
                mock = Build.VERSION.SDK_INT >= 31 ? observed.isMock() : observed.isFromMockProvider();
                outcome = mock.booleanValue() ? "mock-marker-observed" : "mock-marker-not-observed";
            }
        } catch (SecurityException ignored) {
            outcome = "mock-or-location-permission-rejected";
        } catch (RuntimeException ignored) {
            // Retain the current fixed stage instead of private exception text.
        } finally {
            if (touched) {
                try {
                    if (providerPresent(manager)) manager.removeTestProvider(PROVIDER);
                    lastCleanupSucceeded = !providerPresent(manager);
                } catch (RuntimeException ignored) {
                    lastCleanupSucceeded = false;
                }
            }
            try { present = providerPresent(manager); }
            catch (RuntimeException ignored) { lastCleanupSucceeded = false; }
            if (!lastCleanupSucceeded) outcome = "test-provider-cleanup-required";
        }
        return result("fixture", outcome, selected, fine, injected, readBack, mock, lastCleanupSucceeded, present);
    }

    /** Remove the named fixture only; all standard providers stay untouched. */
    public static synchronized void stop(Context context) {
        try {
            LocationManager manager = manager(context);
            if (providerPresent(manager)) manager.removeTestProvider(PROVIDER);
            lastCleanupSucceeded = !providerPresent(manager);
        } catch (RuntimeException ignored) {
            lastCleanupSucceeded = false;
        }
    }
}

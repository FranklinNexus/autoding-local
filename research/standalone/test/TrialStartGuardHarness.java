package org.autoding.research;

public final class TrialStartGuardHarness {
    private static int checks;
    public static void main(String[] args) {
        check("", false, false, true, -1, false, true);
        check("wall-trial-usb-state-unknown", true, false, false, 1, true, false);
        check("wall-trial-usb-still-connected", true, true, true, 2, true, false);
        check("", true, true, false, 0, true, false);
        check("", true, true, false, -1, true, false);
        check("", true, true, false, 8, true, false);
        check("wall-trial-lockscreen-unconfirmed", true, true, false, 1, false, false);
        check("wall-trial-lockscreen-unconfirmed", true, true, false, 1, true, true);
        check("", true, true, false, 1, true, false);
        check("", true, true, false, 2, true, false);
        check("", true, true, false, 4, true, false);
        System.out.println("PASS: " + checks + " wall-trial start checks");
    }
    private static void check(String expected, boolean required, boolean known, boolean connected,
                              int power, boolean locked, boolean interactive) {
        String actual = TrialStartGuard.rejection(required, known, connected, power, locked, interactive);
        if (!expected.equals(actual)) throw new AssertionError("Unexpected wall-trial start decision");
        checks++;
    }
}

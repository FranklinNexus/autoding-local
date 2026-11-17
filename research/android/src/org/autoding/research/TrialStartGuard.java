package org.autoding.research;

/** Wall-power trial preconditions, checked before waking or entering a PIN. */
public final class TrialStartGuard {
    private TrialStartGuard() {}
    public static String rejection(boolean required, boolean usbKnown, boolean usbConnected,
                                   int powerSource, boolean locked, boolean interactive) {
        if (!required) return "";
        if (!usbKnown) return "wall-trial-usb-state-unknown";
        if (usbConnected) return "wall-trial-usb-still-connected";
        if (powerSource != 1 && powerSource != 2 && powerSource != 4)
            return "wall-trial-charger-unconfirmed";
        if (!locked || interactive) return "wall-trial-lockscreen-unconfirmed";
        return "";
    }
}

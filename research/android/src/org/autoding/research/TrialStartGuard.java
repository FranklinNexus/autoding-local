package org.autoding.research;

/** Optional detached-phone test preconditions. Battery power is accepted. */
public final class TrialStartGuard {
    private TrialStartGuard() {}
    public static String rejection(boolean required, boolean usbKnown, boolean usbConnected,
                                   int powerSource, boolean locked, boolean interactive) {
        if (!required) return "";
        if (!usbKnown) return "wall-trial-usb-state-unknown";
        if (usbConnected) return "wall-trial-usb-still-connected";
        if (!locked || interactive) return "wall-trial-lockscreen-unconfirmed";
        return "";
    }
}

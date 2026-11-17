package org.autoding.research;
public final class PinStore {
    public static boolean blocked, saved = true;
    public static int begins;
    public static char[] secret, lastRead;
    public static boolean isAttemptBlocked(Object context) { return blocked; }
    public static boolean isSaved(Object context) { return saved; }
    public static char[] read(Object context) { lastRead = secret.clone(); return lastRead; }
    public static boolean beginAttempt(Object context) { if (blocked) return false; begins++; blocked = true; return true; }
    public static boolean resetAttempt(Object context) { blocked = false; return true; }
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen;

/** Fixed clock used only by the disposable dry-run compilation. */
final class FixtureClock {
    private static long current;
    static void set(long epochMs) {
        if (epochMs <= 0) throw new IllegalArgumentException("Fixture time must be positive");
        current = epochMs;
    }
    static long now() {
        if (current == 0) throw new IllegalStateException("Fixture clock was not initialized");
        return current;
    }
}

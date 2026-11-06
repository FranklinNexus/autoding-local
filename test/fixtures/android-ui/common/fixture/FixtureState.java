/* SPDX-License-Identifier: GPL-3.0-or-later */
package fixture;

public final class FixtureState {
    public static int connects, flag = -1, disconnects, quits;
    public static boolean noRoot, failSerialization;
    public static void reset() {
        connects = disconnects = quits = 0;
        flag = -1;
        noRoot = failSerialization = false;
    }
}

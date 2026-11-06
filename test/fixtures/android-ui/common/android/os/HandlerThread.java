/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.os;
public final class HandlerThread {
    public HandlerThread(String name) {}
    public void start() {}
    public Looper getLooper() { return new Looper(); }
    public boolean quitSafely() { fixture.FixtureState.quits++; return true; }
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.app;
import android.os.Looper;
/** Android 6 has no connect(int) overload. */
public final class UiAutomation extends UiAutomationBase {
    public UiAutomation(Looper looper, IUiAutomationConnection connection) {}
    public void connect() { fixture.FixtureState.connects++; }
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.app;
import android.os.Looper;
/** Android 7+ must preserve other accessibility services using connect(1). */
public final class UiAutomation extends UiAutomationBase {
    public UiAutomation(Looper looper, IUiAutomationConnection connection) {}
    public void connect() { throw new AssertionError("API 24+ must connect with flags"); }
    public void connect(int flags) {
        fixture.FixtureState.connects++;
        fixture.FixtureState.flag = flags;
    }
}

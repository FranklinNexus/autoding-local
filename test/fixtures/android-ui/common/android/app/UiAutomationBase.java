/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.app;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.view.accessibility.AccessibilityNodeInfo;
public class UiAutomationBase {
    public AccessibilityServiceInfo getServiceInfo() { return new AccessibilityServiceInfo(); }
    public void setServiceInfo(AccessibilityServiceInfo info) {}
    public AccessibilityNodeInfo getRootInActiveWindow() {
        return fixture.FixtureState.noRoot ? null : AccessibilityNodeInfo.fixture();
    }
    public void disconnect() { fixture.FixtureState.disconnects++; }
}

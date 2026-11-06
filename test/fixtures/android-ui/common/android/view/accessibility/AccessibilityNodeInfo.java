/* SPDX-License-Identifier: GPL-3.0-or-later */
package android.view.accessibility;
import android.graphics.Rect;
public final class AccessibilityNodeInfo {
    private final boolean password;
    private final String text, description;
    private final AccessibilityNodeInfo[] children;
    private AccessibilityNodeInfo(boolean password, String text, String description, AccessibilityNodeInfo... children) {
        this.password = password; this.text = text; this.description = description; this.children = children;
    }
    public static AccessibilityNodeInfo fixture() {
        return new AccessibilityNodeInfo(false, "", "",
            new AccessibilityNodeInfo(false, "Fixture title", "Visible title"),
            new AccessibilityNodeInfo(true, "SYNTHETIC_SECRET_TEXT", "SYNTHETIC_SECRET_DESCRIPTION"));
    }
    public boolean isVisibleToUser() { return true; }
    public CharSequence getPackageName() { return "com.android.systemui"; }
    public CharSequence getClassName() { return password ? "android.widget.EditText" : "android.widget.TextView"; }
    public String getViewIdResourceName() { return password ? "com.android.systemui:id/pinEntry" : ""; }
    public CharSequence getText() { return text; }
    public CharSequence getContentDescription() { return description; }
    public boolean isPassword() { return password; }
    public boolean isEnabled() { return true; }
    public boolean isFocused() { return password; }
    public void getBoundsInScreen(Rect rect) {}
    public int getChildCount() { return children.length; }
    public AccessibilityNodeInfo getChild(int index) { return children[index]; }
    public void recycle() {}
}

/* SPDX-License-Identifier: GPL-3.0-or-later */
package xcom.warof.chosen.tools;

import android.app.UiAutomation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.graphics.Rect;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Build;
import android.os.SystemClock;
import android.util.Xml;
import android.view.accessibility.AccessibilityNodeInfo;
import org.xmlpull.v1.XmlSerializer;

/** Shell-only UI snapshot. Dynamic lock screens need no waitForIdle call. */
public final class ReadWindow {
    public static void main(String[] args) throws Exception {
        HandlerThread thread = new HandlerThread("AutoDingUiSnapshot");
        thread.start();
        UiAutomation automation = null;
        try {
            Class<?> connectionType = Class.forName("android.app.IUiAutomationConnection");
            Object connection = Class.forName("android.app.UiAutomationConnection").getConstructor().newInstance();
            automation = (UiAutomation)UiAutomation.class.getConstructor(Looper.class, connectionType)
                .newInstance(thread.getLooper(), connection);
            if (Build.VERSION.SDK_INT >= 24) {
                UiAutomation.class.getMethod("connect", int.class).invoke(automation, 1);
            } else {
                // Android 6 exposes only connect(). Unlike FLAG_DONT_SUPPRESS
                // on newer releases, it temporarily suppresses accessibility
                // services until this short snapshot disconnects.
                UiAutomation.class.getMethod("connect").invoke(automation);
            }
            AccessibilityServiceInfo info = automation.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            automation.setServiceInfo(info);
            AccessibilityNodeInfo root = null;
            for (int n = 0; n < 20 && root == null; n++) {
                root = automation.getRootInActiveWindow();
                if (root == null) SystemClock.sleep(100);
            }
            if (root == null) throw new IllegalStateException("No active window");
            XmlSerializer xml = Xml.newSerializer();
            xml.setOutput(System.out, "UTF-8");
            xml.startDocument("UTF-8", true);
            xml.startTag("", "hierarchy");
            writeNode(xml, root, 0);
            xml.endTag("", "hierarchy");
            xml.endDocument();
            xml.flush();
            root.recycle();
        } finally {
            if (automation != null) {
                try { UiAutomation.class.getMethod("disconnect").invoke(automation); }
                catch (Exception ignored) {}
            }
            thread.quitSafely();
        }
    }
    private static String value(CharSequence text) { return text == null ? "" : text.toString(); }
    private static void writeNode(XmlSerializer xml, AccessibilityNodeInfo node, int depth) throws Exception {
        if (depth > 60 || !node.isVisibleToUser()) return;
        xml.startTag("", "node");
        xml.attribute("", "package", value(node.getPackageName()));
        xml.attribute("", "class", value(node.getClassName()));
        xml.attribute("", "resource-id", value(node.getViewIdResourceName()));
        // Never return password contents, including masking characters or length.
        xml.attribute("", "text", node.isPassword() ? "" : value(node.getText()));
        xml.attribute("", "content-desc", node.isPassword() ? "" : value(node.getContentDescription()));
        xml.attribute("", "password", String.valueOf(node.isPassword()));
        xml.attribute("", "input-empty", node.getText() == null || node.getText().length() == 0 ? "true" : "false");
        xml.attribute("", "enabled", String.valueOf(node.isEnabled()));
        xml.attribute("", "focused", String.valueOf(node.isFocused()));
        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
        xml.attribute("", "bounds", bounds.toShortString());
        for (int n = 0; n < node.getChildCount(); n++) {
            AccessibilityNodeInfo child = node.getChild(n);
            if (child != null) { writeNode(xml, child, depth + 1); child.recycle(); }
        }
        xml.endTag("", "node");
    }
}

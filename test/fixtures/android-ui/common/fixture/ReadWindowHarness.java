/* SPDX-License-Identifier: GPL-3.0-or-later */
package fixture;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import xcom.warof.chosen.tools.ReadWindow;

/** JVM checks of the real shell helper; these are not Android device tests. */
public final class ReadWindowHarness {
    private static int checks;
    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError(description);
        checks++;
    }
    private static String snapshot() throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(captured, true, "UTF-8")) {
            System.setOut(output);
            ReadWindow.main(new String[0]);
            return captured.toString("UTF-8");
        } finally {
            System.setOut(previous);
        }
    }
    private static void cleaned(String label) {
        check(FixtureState.disconnects == 1, label + ": disconnect called once");
        check(FixtureState.quits == 1, label + ": handler thread stopped once");
    }
    public static void main(String[] args) throws Exception {
        int api = android.os.Build.VERSION.SDK_INT;
        FixtureState.reset();
        String xml = snapshot();
        check(FixtureState.connects == 1, "one connection");
        check(FixtureState.flag == (api >= 24 ? 1 : -1), "API-specific connect overload and flags");
        cleaned("success");
        javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        org.w3c.dom.Document document = factory.newDocumentBuilder().parse(
            new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
        org.w3c.dom.NodeList nodes = document.getElementsByTagName("node");
        check(nodes.getLength() == 3, "visible fixture tree is serialized");
        org.w3c.dom.Element password = (org.w3c.dom.Element)nodes.item(2);
        check("true".equals(password.getAttribute("password")), "password marker remains");
        check(password.getAttribute("text").isEmpty(), "password text is blank");
        check(password.getAttribute("content-desc").isEmpty(), "password description is blank");
        check("false".equals(password.getAttribute("input-empty")), "nonempty state remains available for PIN guard");
        check(!xml.contains("SYNTHETIC_SECRET"), "synthetic secret absent from XML");
        check(xml.contains("Fixture title"), "ordinary visible text remains available");

        FixtureState.reset();
        FixtureState.noRoot = true;
        boolean failed = false;
        try { snapshot(); } catch (IllegalStateException expected) { failed = true; }
        check(failed, "missing active window fails");
        cleaned("missing window");

        FixtureState.reset();
        FixtureState.failSerialization = true;
        failed = false;
        try { snapshot(); } catch (java.io.IOException expected) { failed = true; }
        check(failed, "serialization error propagates");
        cleaned("serialization failure");
        System.out.println("PASS: " + checks + " real ReadWindow fixture checks for API " + api);
    }
}

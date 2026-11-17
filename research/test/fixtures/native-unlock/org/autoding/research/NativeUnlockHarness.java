package org.autoding.research;
import android.graphics.Rect;
import android.os.Handler;
import android.view.accessibility.AccessibilityNodeInfo;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.util.function.Consumer;

public final class NativeUnlockHarness {
    static int checks, clicks;
    static String outcome;
    static boolean success;
    static int callbacks;
    static void check(boolean condition, String name) { if (!condition) throw new AssertionError(name); checks++; }
    static AccessibilityNodeInfo keypad() {
        AccessibilityNodeInfo root = new AccessibilityNodeInfo();
        AccessibilityNodeInfo entry = new AccessibilityNodeInfo();
        entry.id = root.pkg + ":id/fixedPinEntry"; entry.focused = true; entry.password = true;
        entry.bounds = new Rect(100, 500, 900, 600); root.children.add(entry);
        for (int n = 0; n < 10; n++) {
            AccessibilityNodeInfo key = new AccessibilityNodeInfo();
            key.id = root.pkg + ":id/key" + n; key.text = Integer.toString(n); key.clickable = true;
            key.bounds = new Rect(100 + n % 3 * 250, 900 + n / 3 * 200, 300 + n % 3 * 250, 1050 + n / 3 * 200);
            root.children.add(key);
        }
        return root;
    }
    static ProbeAccessibilityService reset() {
        NativeUnlock.cancel(); Handler.reset();
        ProbeAccessibilityService service = new ProbeAccessibilityService(); service.root = keypad(); ProbeAccessibilityService.active = service;
        PinStore.blocked = false; PinStore.saved = true; PinStore.begins = 0;
        PinStore.secret = new char[]{'1','2','3','4'}; PinStore.lastRead = null;
        clicks = 0; callbacks = 0; outcome = null; success = false;
        AccessibilityNodeInfo.clickHandler = node -> { clicks++; service.root.children.get(0).text += "\u2022"; };
        return service;
    }
    static void start(ProbeAccessibilityService service) {
        NativeUnlock.start(service, (ok, code) -> { success = ok; outcome = code; callbacks++; });
    }
    static String inspect(AccessibilityNodeInfo root) throws Exception {
        ProbeAccessibilityService service = new ProbeAccessibilityService(); service.root = root;
        Method inspect = NativeUnlock.class.getDeclaredMethod("inspect", ProbeAccessibilityService.class, boolean.class);
        inspect.setAccessible(true); Object result = inspect.invoke(null, service, true);
        Field code = result.getClass().getDeclaredField("code"); code.setAccessible(true);
        String value = (String)code.get(result); ((AutoCloseable)result).close(); return value;
    }
    static void reject(String name, Consumer<AccessibilityNodeInfo> change, String expected) throws Exception {
        AccessibilityNodeInfo root = keypad(); change.accept(root); check(expected.equals(inspect(root)), name);
    }
    public static void main(String[] ignored) throws Exception {
        check("pin-ui-verified".equals(inspect(keypad())), "complete fresh keypad accepted");
        reject("other package", r -> r.pkg = "example.fixture", "pin-system-window-required");
        reject("redacted empty not established", r -> r.children.get(0).text = null, "pin-empty-unverified");
        reject("existing input", r -> r.children.get(0).text = "\u2022", "pin-empty-unverified");
        reject("unfocused input", r -> r.children.get(0).focused = false, "pin-entry-not-ready");
        reject("non-password input", r -> r.children.get(0).password = false, "pin-entry-not-ready");
        reject("missing digit", r -> r.children.remove(3), "pin-keypad-incomplete");
        reject("duplicate digit", r -> r.children.add(keypad().children.get(1)), "pin-keypad-ambiguous");
        reject("foreign digit", r -> r.children.get(1).pkg = "example.fixture", "pin-keypad-incomplete");
        reject("different window", r -> r.children.get(1).window++, "pin-keypad-incomplete");
        reject("not clickable", r -> r.children.get(1).clickable = false, "pin-keypad-ambiguous");
        reject("missing click action", r -> r.children.get(1).actions = 0, "pin-keypad-ambiguous");
        reject("conflicting label", r -> r.children.get(1).text = "8", "pin-key-label-mismatch");
        reject("overlapping keys", r -> r.children.get(1).bounds = r.children.get(2).bounds, "pin-keypad-geometry-invalid");
        reject("entry overlaps key", r -> r.children.get(0).bounds = r.children.get(1).bounds, "pin-keypad-geometry-invalid");
        reject("ambiguous entry", r -> r.children.add(keypad().children.get(0)), "pin-entry-ambiguous");
        reject("stale password node", r -> r.children.get(0).refreshResult = false, "pin-entry-stale");

        ProbeAccessibilityService service = reset();
        final ProbeAccessibilityService unlock = service;
        AccessibilityNodeInfo.clickHandler = node -> { clicks++; unlock.root.children.get(0).text += "\u2022"; if (clicks == 4) unlock.keyguard.locked = false; };
        start(service); Handler.drain();
        check(success && "verified-unlocked".equals(outcome) && clicks == 4, "one complete verified attempt");
        check(!PinStore.blocked && PinStore.begins == 1 && callbacks == 1, "verified success clears exactly one guard");
        check(!service.power.lock.held, "wake released");
        check(java.util.Arrays.equals(PinStore.lastRead, new char[4]), "PIN char array wiped");

        service = reset(); start(service); Handler.drain();
        check(!success && "pin-not-verified".equals(outcome) && clicks == 4 && PinStore.blocked, "failed verification stops after one PIN");
        start(service); Handler.drain();
        check("pin-attempt-blocked".equals(outcome) && clicks == 4 && PinStore.begins == 1, "persistent guard prevents retry");

        service = reset(); PinStore.secret = new char[]{'1','2','3','4','5','6','7','8'};
        final ProbeAccessibilityService resetEntry = service;
        AccessibilityNodeInfo.clickHandler = node -> { clicks++; resetEntry.root.children.get(0).text += "\u2022"; if (clicks == 6) resetEntry.root.children.get(0).text = ""; };
        start(service); Handler.drain();
        check(clicks == 6 && "pin-input-state-changed".equals(outcome) && PinStore.blocked, "automatic rejection reset never receives remaining digits");

        service = reset(); final ProbeAccessibilityService redact = service;
        AccessibilityNodeInfo.clickHandler = node -> { clicks++; redact.root.children.get(0).text = null; };
        start(service); Handler.drain();
        check(clicks == 1 && "pin-input-state-changed".equals(outcome), "unknown post-key length stops continuation");

        service = reset(); start(service); Handler.step(); NativeUnlock.cancel(); Handler.drain();
        check(clicks == 0 && !PinStore.blocked && "unlock-cancelled".equals(outcome), "cancel before attempt causes zero keys");
        service = reset(); start(service); while (clicks == 0) Handler.step(); NativeUnlock.cancel(); Handler.drain();
        check(clicks == 1 && PinStore.blocked && "unlock-cancelled".equals(outcome), "cancel after first key stops all remaining keys and preserves guard");

        service = reset(); start(service); NativeUnlock.start(service, (ok, code) -> check("unlock-busy".equals(code), "concurrent request rejected"));
        NativeUnlock.cancel(); Handler.drain();
        service = reset(); service.root = null; service.loseGestureCallback = true; start(service); Handler.drain();
        check(clicks == 0 && "unlock-timeout".equals(outcome) && callbacks == 1, "missing gesture callback terminates at deadline");
        service = reset(); service.keyguard.locked = false; start(service); Handler.drain();
        check(success && "already-unlocked".equals(outcome) && clicks == 0 && PinStore.begins == 0, "unlocked phone does not consume attempt");
        service = reset(); start(service); ProbeAccessibilityService.active = null; Handler.drain();
        check(clicks == 0 && "service-disconnected".equals(outcome), "service loss stops before typing");

        service = reset(); final ProbeAccessibilityService cache = service;
        AccessibilityNodeInfo cachedEntry = cache.root.children.get(0);
        cachedEntry.refreshHandler = node -> node.text = "\u2022".repeat(clicks);
        AccessibilityNodeInfo.clickHandler = node -> { clicks++; if (clicks == 4) cache.keyguard.locked = false; };
        start(service); Handler.drain();
        check(success && clicks == 4 && cachedEntry.refreshCount >= 4, "fresh password node defeats unchanged child cache");

        service = reset(); final ProbeAccessibilityService delayed = service;
        AccessibilityNodeInfo.clickHandler = node -> {
            clicks++; final int progress = clicks;
            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                delayed.root.children.get(0).text = "\u2022".repeat(progress);
                if (progress == 4) delayed.keyguard.locked = false;
            }, 650);
        };
        start(service); Handler.drain();
        check(success && clicks == 4 && PinStore.begins == 1, "delayed acknowledgements wait without repeating digits");

        service = reset(); AccessibilityNodeInfo.clickHandler = node -> clicks++;
        start(service); Handler.drain();
        check(clicks == 1 && "pin-progress-unconfirmed".equals(outcome) && PinStore.blocked, "missing acknowledgement times out without resending");

        service = reset(); final ProbeAccessibilityService resetAfterAck = service;
        AccessibilityNodeInfo.clickHandler = node -> {
            clicks++; resetAfterAck.root.children.get(0).text = "\u2022";
            new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> resetAfterAck.root.children.get(0).text = "", 170);
        };
        start(service); Handler.drain();
        check(clicks == 1 && "pin-input-state-changed".equals(outcome), "reset after confirmed progress prevents next digit");

        service = reset(); final ProbeAccessibilityService jumped = service;
        AccessibilityNodeInfo.clickHandler = node -> { clicks++; jumped.root.children.get(0).text = "\u2022\u2022"; };
        start(service); Handler.drain();
        check(clicks == 1 && "pin-input-state-changed".equals(outcome), "unexpected extra progress stops immediately");

        service = reset(); AccessibilityNodeInfo.clickHandler = node -> clicks++;
        start(service); while (clicks == 0) Handler.step(); NativeUnlock.cancel(); Handler.drain();
        check(clicks == 1 && "unlock-cancelled".equals(outcome) && PinStore.blocked, "cancelling during acknowledgement wait preserves one-key limit");
        System.out.println("Passed " + checks + " native unlock fixture checks; no device or real credential accessed.");
    }
}

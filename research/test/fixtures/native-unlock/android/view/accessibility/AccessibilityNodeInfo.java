package android.view.accessibility;
import java.util.ArrayList;
import android.graphics.Rect;
public final class AccessibilityNodeInfo {
    public static final int ACTION_CLICK = 16;
    public static java.util.function.Consumer<AccessibilityNodeInfo> clickHandler;
    public java.util.function.Consumer<AccessibilityNodeInfo> refreshHandler;
    public int refreshCount;
    public String pkg = "com.android.systemui", id = "", text = "", description = "";
    public boolean visible = true, enabled = true, focused, password, clickable, clickResult = true, refreshResult = true;
    public int window = 7, actions = ACTION_CLICK;
    public Rect bounds = new Rect(0, 0, 1080, 2400);
    public final ArrayList<AccessibilityNodeInfo> children = new ArrayList<>();
    public CharSequence getPackageName() { return pkg; }
    public int getWindowId() { return window; }
    public String getViewIdResourceName() { return id; }
    public CharSequence getText() { return text; }
    public CharSequence getContentDescription() { return description; }
    public boolean isVisibleToUser() { return visible; }
    public boolean isEnabled() { return enabled; }
    public boolean isFocused() { return focused; }
    public boolean isPassword() { return password; }
    public boolean isClickable() { return clickable; }
    public int getActions() { return actions; }
    public int getChildCount() { return children.size(); }
    public AccessibilityNodeInfo getChild(int at) { return children.get(at); }
    public void getBoundsInScreen(Rect output) { output.left = bounds.left; output.top = bounds.top; output.right = bounds.right; output.bottom = bounds.bottom; }
    public boolean refresh() { refreshCount++; if (refreshHandler != null) refreshHandler.accept(this); return refreshResult; }
    public boolean performAction(int action) { if (action != ACTION_CLICK) throw new AssertionError("unexpected action"); if (clickHandler != null) clickHandler.accept(this); return clickResult; }
    public void recycle() {}
}

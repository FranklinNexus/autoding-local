package android.graphics;
public final class Rect {
    public int left, top, right, bottom;
    public Rect() {}
    public Rect(int l, int t, int r, int b) { left = l; top = t; right = r; bottom = b; }
    public boolean isEmpty() { return left >= right || top >= bottom; }
    public static boolean intersects(Rect a, Rect b) { return a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom; }
}

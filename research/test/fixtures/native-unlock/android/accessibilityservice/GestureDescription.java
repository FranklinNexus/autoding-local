package android.accessibilityservice;
public final class GestureDescription {
    public static final class StrokeDescription { public StrokeDescription(android.graphics.Path path, long start, long duration) {} }
    public static final class Builder {
        public Builder addStroke(StrokeDescription stroke) { return this; }
        public GestureDescription build() { return new GestureDescription(); }
    }
}

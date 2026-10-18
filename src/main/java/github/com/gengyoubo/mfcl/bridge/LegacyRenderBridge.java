package github.com.gengyoubo.mfcl.bridge;

/** Routes legacy GL state to whichever capture is active: the 2D GUI frame first, then the 3D world frame. */
public final class LegacyRenderBridge {
    private LegacyRenderBridge() { }
    public static void push() { if (LegacyGuiGraphics.capturing()) LegacyGuiGraphics.push(); LegacyWorldGraphics.push(); }
    public static void pop() { if (LegacyGuiGraphics.capturing()) LegacyGuiGraphics.pop(); LegacyWorldGraphics.pop(); }
    public static void translate(double x, double y, double z) { if (LegacyGuiGraphics.capturing()) LegacyGuiGraphics.translate(x, y, z); LegacyWorldGraphics.translate(x, y, z); }
    public static void scale(double x, double y, double z) { if (LegacyGuiGraphics.capturing()) LegacyGuiGraphics.scale(x, y, z); LegacyWorldGraphics.scale(x, y, z); }
    public static void rotate(double angle, double x, double y, double z) { LegacyWorldGraphics.rotate(angle, x, y, z); }
    public static void loadIdentity() { LegacyWorldGraphics.loadIdentity(); }
    public static int integer(int value) { return LegacyGuiGraphics.capturing() ? LegacyGuiGraphics.integer(value) : LegacyWorldGraphics.integer(value); }
}

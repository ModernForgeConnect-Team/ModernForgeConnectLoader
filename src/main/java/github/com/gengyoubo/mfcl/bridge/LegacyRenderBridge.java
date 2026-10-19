package github.com.gengyoubo.mfcl.bridge;

/** Routes legacy GL state to whichever capture is active: the 2D GUI frame first, then the 3D world frame. */
public final class LegacyRenderBridge {
    private LegacyRenderBridge() { }
    public static void push() { if (LegacyGuiGraphics.capturing()&&LegacyWorldGraphics.modelView()) LegacyGuiGraphics.push(); LegacyWorldGraphics.push(); }
    public static void pop() { if (LegacyGuiGraphics.capturing()&&LegacyWorldGraphics.modelView()) LegacyGuiGraphics.pop(); LegacyWorldGraphics.pop(); }
    public static void translate(double x, double y, double z) { if (LegacyGuiGraphics.capturing()&&LegacyWorldGraphics.modelView()) LegacyGuiGraphics.translate(x, y, z); LegacyWorldGraphics.translate(x, y, z); }
    public static void scale(double x, double y, double z) { if (LegacyGuiGraphics.capturing()&&LegacyWorldGraphics.modelView()) LegacyGuiGraphics.scale(x, y, z); LegacyWorldGraphics.scale(x, y, z); }
    public static void rotate(double angle, double x, double y, double z) { LegacyWorldGraphics.rotate(angle, x, y, z); }
    public static void loadIdentity() { if(LegacyGuiGraphics.capturing()&&LegacyWorldGraphics.modelView())LegacyGuiGraphics.loadIdentity();LegacyWorldGraphics.loadIdentity(); }
    public static void clear(int mask) {
        // GUI replay owns draw order and overlays; it must never clear the host scene's depth buffer.
        if(!LegacyGuiGraphics.capturing()||mask!=256)throw new UnsupportedOperationException("Legacy capture cannot clear buffer mask "+mask);
    }
    public static void ortho(double left,double right,double bottom,double top,double near,double far) {
        if(!LegacyGuiGraphics.capturing()||LegacyWorldGraphics.modelView())throw new UnsupportedOperationException("Legacy ortho requires a GUI projection matrix");
        LegacyGuiGraphics.pixelProjection(left,right,bottom,top);
    }
    public static int integer(int value) { return LegacyGuiGraphics.capturing() ? LegacyGuiGraphics.integer(value) : LegacyWorldGraphics.integer(value); }
}

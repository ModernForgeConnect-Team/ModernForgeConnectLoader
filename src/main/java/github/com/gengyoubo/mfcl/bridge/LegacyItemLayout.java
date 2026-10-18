package github.com.gengyoubo.mfcl.bridge;

import java.util.List;

/** Fits overflowing inventory geometry after projection; ordinary block display transforms stay intact. */
public final class LegacyItemLayout {
    private LegacyItemLayout() {}
    public record Bounds(double minX, double minY, double maxX, double maxY) {}
    public record GuiFit(double scale, double x, double y) {
        public static final GuiFit IDENTITY = new GuiFit(1, 0, 0);
        public Bounds apply(Bounds bounds) {
            return new Bounds(bounds.minX()*scale+x, bounds.minY()*scale+y, bounds.maxX()*scale+x, bounds.maxY()*scale+y);
        }
    }
    public static Bounds guiBounds(List<LegacyVertexStream.Primitive> primitives) { return guiBounds(primitives, true); }
    /**
     * {@code blockRotation} must mirror the matrix actually applied by the renderer: ISBRH block captures get the
     * isometric block rotation, while an original IItemRenderer already supplies its own inventory orientation.
     */
    public static Bounds guiBounds(List<LegacyVertexStream.Primitive> primitives, boolean blockRotation) {
        double[] matrix = LegacyVertexStream.identity();
        if (blockRotation) {
            LegacyVertexStream.rotate(matrix, 30, 1, 0, 0);
            LegacyVertexStream.rotate(matrix, 225, 0, 1, 0);
        }
        LegacyVertexStream.scale(matrix, .625, .625, .625);
        double minX = Double.POSITIVE_INFINITY, minY = minX, maxX = -minX, maxY = maxX;
        for (var primitive : primitives) for (var vertex : primitive.vertices()) {
            double[] point = LegacyVertexStream.transform(matrix, vertex.x(), vertex.y(), vertex.z());
            minX = Math.min(minX, point[0]); minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]); maxY = Math.max(maxY, point[1]);
        }
        return Double.isFinite(minX) ? new Bounds(minX, minY, maxX, maxY) : new Bounds(0, 0, 0, 0);
    }
    public static GuiFit fitGui(List<LegacyVertexStream.Primitive> primitives) { return fitGui(primitives, true); }
    public static GuiFit fitGui(List<LegacyVertexStream.Primitive> primitives, boolean blockRotation) {
        Bounds bounds = guiBounds(primitives, blockRotation);
        if (bounds.minX() >= -.5 && bounds.minY() >= -.5 && bounds.maxX() <= .5 && bounds.maxY() <= .5)
            return GuiFit.IDENTITY;
        // Leave a small slot margin; translate in projected space so asymmetric old origins do not drift.
        double extent = Math.max(bounds.maxX()-bounds.minX(), bounds.maxY()-bounds.minY());
        double scale = extent > .9 ? .9 / extent : 1;
        return new GuiFit(scale, -(bounds.minX()+bounds.maxX())*.5*scale, -(bounds.minY()+bounds.maxY())*.5*scale);
    }
}

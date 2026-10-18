package github.com.gengyoubo.mfcl.bridge;

import java.util.List;

/** Pure legacy geometry and matrix math. No host OpenGL type or context is ever entered. */
public final class LegacyVertexStream {
    public record Vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz, int argb) {}
    public record RenderState(boolean blend, int sourceBlend, int destinationBlend, boolean depthWrite, boolean cull) {
        public static final RenderState OPAQUE = new RenderState(false, 770, 771, true, false);
    }
    public record Primitive(int mode, String texture, List<Vertex> vertices, RenderState state) {
        public Primitive(int mode, String texture, List<Vertex> vertices) { this(mode, texture, vertices, RenderState.OPAQUE); }
        public Primitive { vertices = List.copyOf(vertices); }
    }
    /** Modern consumer of captured geometry: one primitive at a time, already in legacy world space. */
    public interface Sink {
        void begin(int mode, String texture, int vertexCount);
        void vertex(Vertex vertex);
        default void end() { }
    }
    public static final int GL_TRIANGLES = 4, GL_TRIANGLE_STRIP = 5, GL_TRIANGLE_FAN = 6,
            GL_QUADS = 7, GL_QUAD_STRIP = 8, GL_POLYGON = 9;
    private LegacyVertexStream() { }
    public static double[] identity() { return new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}; }
    public static double[] copy(double[] matrix) { return matrix.clone(); }
    public static void multiply(double[] left, double[] right) {
        double[] result = new double[16];
        for (int column = 0; column < 4; column++) for (int row = 0; row < 4; row++) {
            double sum = 0;
            for (int k = 0; k < 4; k++) sum += left[k * 4 + row] * right[column * 4 + k];
            result[column * 4 + row] = sum;
        }
        System.arraycopy(result, 0, left, 0, 16);
    }
    public static void translate(double[] matrix, double x, double y, double z) {
        multiply(matrix, new double[]{1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, x, y, z, 1});
    }
    public static void scale(double[] matrix, double x, double y, double z) {
        multiply(matrix, new double[]{x, 0, 0, 0, 0, y, 0, 0, 0, 0, z, 0, 0, 0, 0, 1});
    }
    public static void rotate(double[] matrix, double angleDegrees, double x, double y, double z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0) return;
        x /= length; y /= length; z /= length;
        double radians = Math.toRadians(angleDegrees), c = Math.cos(radians), s = Math.sin(radians), t = 1 - c;
        // Row-major rotation, written column-major for multiply().
        multiply(matrix, new double[]{
                t * x * x + c, t * x * y + s * z, t * x * z - s * y, 0,
                t * x * y - s * z, t * y * y + c, t * y * z + s * x, 0,
                t * x * z + s * y, t * y * z - s * x, t * z * z + c, 0,
                0, 0, 0, 1});
    }
    /** Inverse of an affine column-major matrix, used to store display-list geometry in local space. */
    public static double[] invert(double[] m) {
        double a00 = m[0], a01 = m[4], a02 = m[8];
        double a10 = m[1], a11 = m[5], a12 = m[9];
        double a20 = m[2], a21 = m[6], a22 = m[10];
        double determinant = a00 * (a11 * a22 - a12 * a21) - a01 * (a10 * a22 - a12 * a20) + a02 * (a10 * a21 - a11 * a20);
        if (determinant == 0) return identity();
        double inv = 1.0 / determinant;
        double b00 = (a11 * a22 - a12 * a21) * inv, b01 = (a02 * a21 - a01 * a22) * inv, b02 = (a01 * a12 - a02 * a11) * inv;
        double b10 = (a12 * a20 - a10 * a22) * inv, b11 = (a00 * a22 - a02 * a20) * inv, b12 = (a02 * a10 - a00 * a12) * inv;
        double b20 = (a10 * a21 - a11 * a20) * inv, b21 = (a01 * a20 - a00 * a21) * inv, b22 = (a00 * a11 - a01 * a10) * inv;
        double tx = m[12], ty = m[13], tz = m[14];
        return new double[]{
                b00, b10, b20, 0,
                b01, b11, b21, 0,
                b02, b12, b22, 0,
                -(b00 * tx + b01 * ty + b02 * tz), -(b10 * tx + b11 * ty + b12 * tz), -(b20 * tx + b21 * ty + b22 * tz), 1};
    }
    public static double[] transform(double[] matrix, double x, double y, double z) {
        return new double[]{
                matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12],
                matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13],
                matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14]};
    }
    public static void replay(List<Primitive> primitives, Sink sink) {
        for (Primitive primitive : primitives) {
            sink.begin(primitive.mode(), primitive.texture(), primitive.vertices().size());
            for (Vertex vertex : primitive.vertices()) sink.vertex(vertex);
            sink.end();
        }
    }
}

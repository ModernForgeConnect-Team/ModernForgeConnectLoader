package github.com.gengyoubo.mfcl.bridge;

import java.util.List;

/** Pure legacy geometry and matrix math. No host OpenGL type or context is ever entered. */
public final class LegacyVertexStream {
    public record Vertex(double x, double y, double z, float u, float v, float nx, float ny, float nz, int argb) {}
    public record Primitive(int mode, String texture, List<Vertex> vertices) {
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

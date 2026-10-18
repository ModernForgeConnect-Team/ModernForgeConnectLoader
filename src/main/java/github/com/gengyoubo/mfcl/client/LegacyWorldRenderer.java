package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import org.joml.Matrix4f;

import java.util.List;

/** Replays captured legacy geometry through the modern vertex pipeline without a legacy GL context. */
public final class LegacyWorldRenderer {
    private LegacyWorldRenderer() { }
    public static void replay(PoseStack pose, VertexConsumer consumer, List<LegacyVertexStream.Primitive> primitives, int packedLight, int packedOverlay) {
        Matrix4f matrix = pose.last().pose();
        for (LegacyVertexStream.Primitive primitive : primitives) {
            var vertices = primitive.vertices();
            for (int[] triangle : triangles(primitive.mode(), vertices.size())) {
                for (int index : triangle) {
                    LegacyVertexStream.Vertex vertex = vertices.get(index);
                    consumer.vertex(matrix, (float) vertex.x(), (float) vertex.y(), (float) vertex.z())
                            .color(vertex.argb() >> 16 & 0xFF, vertex.argb() >> 8 & 0xFF, vertex.argb() & 0xFF, vertex.argb() >>> 24)
                            .uv(vertex.u(), vertex.v())
                            .overlayCoords(packedOverlay).uv2(packedLight)
                            .normal(vertex.nx(), vertex.ny(), vertex.nz())
                            .endVertex();
                }
            }
        }
    }
    static int[][] triangles(int mode, int count) {
        switch (mode) {
            case LegacyVertexStream.GL_QUADS -> {
                int quads = count / 4;
                int[][] result = new int[quads * 2][];
                for (int q = 0; q < quads; q++) {
                    int base = q * 4;
                    result[q * 2] = new int[]{base, base + 1, base + 2};
                    result[q * 2 + 1] = new int[]{base, base + 2, base + 3};
                }
                return result;
            }
            case LegacyVertexStream.GL_TRIANGLES -> {
                int[][] result = new int[count / 3][];
                for (int t = 0; t < result.length; t++) result[t] = new int[]{t * 3, t * 3 + 1, t * 3 + 2};
                return result;
            }
            case LegacyVertexStream.GL_TRIANGLE_STRIP -> {
                int[][] result = new int[Math.max(0, count - 2)][];
                for (int i = 0; i + 2 < count; i++) result[i] = (i & 1) == 0 ? new int[]{i, i + 1, i + 2} : new int[]{i + 1, i, i + 2};
                return result;
            }
            case LegacyVertexStream.GL_TRIANGLE_FAN, LegacyVertexStream.GL_POLYGON -> {
                int[][] result = new int[Math.max(0, count - 2)][];
                for (int i = 0; i + 2 < count; i++) result[i] = new int[]{0, i + 1, i + 2};
                return result;
            }
            case LegacyVertexStream.GL_QUAD_STRIP -> {
                int[][] result = new int[Math.max(0, count - 2)][];
                for (int i = 0; i + 2 < count; i++) result[i] = (i & 1) == 0 ? new int[]{i, i + 1, i + 2} : new int[]{i + 1, i + 2, i + 3};
                return result;
            }
            default -> { return new int[0][]; }
        }
    }
}

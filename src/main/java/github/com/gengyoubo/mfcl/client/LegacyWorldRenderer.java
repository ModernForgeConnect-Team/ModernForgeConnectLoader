package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix4f;

import java.util.List;

/** Replays captured legacy geometry through the modern vertex pipeline without a legacy GL context. */
public final class LegacyWorldRenderer {
    private LegacyWorldRenderer() { }
    /** Local captured UVs are mapped onto a modern atlas sprite, so scaled and animated textures sample correctly. */
    public static void replay(PoseStack pose, VertexConsumer consumer, TextureAtlasSprite sprite, List<LegacyVertexStream.Primitive> primitives, int packedLight, int packedOverlay) {
        Matrix4f matrix = pose.last().pose();
        for (LegacyVertexStream.Primitive primitive : primitives) {
            var vertices = primitive.vertices();
            for (int[] quad : quads(primitive.mode(), vertices.size())) {
                for (int index : quad) {
                    LegacyVertexStream.Vertex vertex = vertices.get(index);
                    consumer.vertex(matrix, (float) vertex.x(), (float) vertex.y(), (float) vertex.z())
                            .color(vertex.argb() >> 16 & 0xFF, vertex.argb() >> 8 & 0xFF, vertex.argb() & 0xFF, vertex.argb() >>> 24)
                            // TextureAtlasSprite.getU/getV take a 0..16 icon coordinate, not 0..1.
                            .uv(sprite.getU(vertex.u() * 16.0), sprite.getV(vertex.v() * 16.0))
                            .overlayCoords(packedOverlay).uv2(packedLight)
                            .normal(pose.last().normal(), vertex.nx(), vertex.ny(), vertex.nz())
                            .endVertex();
                }
            }
        }
    }
    /** Replays geometry whose UVs already address a standalone texture (old model renderers). */
    public static void replayStandalone(PoseStack pose, VertexConsumer consumer, List<LegacyVertexStream.Primitive> primitives, int packedLight, int packedOverlay) {
        Matrix4f matrix = pose.last().pose();
        for (LegacyVertexStream.Primitive primitive : primitives) {
            var vertices = primitive.vertices();
            for (int[] quad : quads(primitive.mode(), vertices.size())) {
                for (int index : quad) {
                    LegacyVertexStream.Vertex vertex = vertices.get(index);
                    consumer.vertex(matrix, (float) vertex.x(), (float) vertex.y(), (float) vertex.z())
                            .color(vertex.argb() >> 16 & 0xFF, vertex.argb() >> 8 & 0xFF, vertex.argb() & 0xFF, vertex.argb() >>> 24)
                            .uv(vertex.u(), vertex.v())
                            .overlayCoords(packedOverlay).uv2(packedLight)
                            .normal(pose.last().normal(), vertex.nx(), vertex.ny(), vertex.nz())
                            .endVertex();
                }
            }
        }
    }
    /** Entity RenderTypes consume QUADS. Triangles use a repeated last vertex, never a six-vertex stream. */
    static int[][] quads(int mode, int count) {
        if (mode == LegacyVertexStream.GL_QUADS) {
            int[][] result = new int[count / 4][];
            for (int q = 0; q < result.length; q++) {
                int base = q * 4;
                result[q] = new int[]{base, base + 1, base + 2, base + 3};
            }
            return result;
        }
        if (mode == LegacyVertexStream.GL_QUAD_STRIP) {
            int[][] result = new int[Math.max(0, count / 2 - 1)][];
            for (int q = 0; q < result.length; q++) {
                int base = q * 2;
                result[q] = new int[]{base, base + 1, base + 3, base + 2};
            }
            return result;
        }
        int[][] result = triangles(mode, count);
        for (int i = 0; i < result.length; i++) {
            int[] triangle = result[i];
            result[i] = new int[]{triangle[0], triangle[1], triangle[2], triangle[2]};
        }
        return result;
    }
    private static int[][] triangles(int mode, int count) {
        switch (mode) {
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
            default -> { return new int[0][]; }
        }
    }
}

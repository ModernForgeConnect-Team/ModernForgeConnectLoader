package github.com.gengyoubo.mfcl.client;

import com.mojang.blaze3d.vertex.*;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import org.junit.jupiter.api.Test;

import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyWorldRendererTest {
    private static LegacyVertexStream.Vertex vertex(double x, double y, double z) {
        return new LegacyVertexStream.Vertex(x, y, z, (float)x, (float)y, 0, 0, 1, 0xFFFFFFFF);
    }

    @Test void cubeFacesStayIntactInTheModernQuadBuffer() {
        var face = List.of(vertex(0, 0, 0), vertex(1, 0, 0), vertex(1, 1, 0), vertex(0, 1, 0));
        var geometry = java.util.stream.IntStream.range(0, 6)
                .mapToObj(i -> new LegacyVertexStream.Primitive(LegacyVertexStream.GL_QUADS, "test", face)).toList();
        var buffer = new BufferBuilder(4096);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        LegacyWorldRenderer.replayStandalone(new PoseStack(), buffer, geometry, 0xF000F0, 0);
        var rendered = buffer.end();
        try {
            assertEquals(24, rendered.drawState().vertexCount(), "Six faces must remain six independent quads");
            assertEquals(36, rendered.drawState().indexCount());
            var bytes = rendered.vertexBuffer().order(ByteOrder.nativeOrder());
            int stride = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
            for (int i = 0; i < 24; i++) {
                var expected = face.get(i % 4);
                assertEquals((float)expected.x(), bytes.getFloat(i * stride));
                assertEquals((float)expected.y(), bytes.getFloat(i * stride + 4));
                assertEquals((float)expected.z(), bytes.getFloat(i * stride + 8));
            }
        } finally { rendered.release(); }
    }

    @Test void trianglesAndStripsProduceCompleteQuadsWithValidWinding() {
        assertArrayEquals(new int[][]{{0, 1, 2, 2}, {3, 4, 5, 5}}, LegacyWorldRenderer.quads(LegacyVertexStream.GL_TRIANGLES, 6));
        assertArrayEquals(new int[][]{{0, 1, 2, 2}, {2, 1, 3, 3}}, LegacyWorldRenderer.quads(LegacyVertexStream.GL_TRIANGLE_STRIP, 4));
        assertArrayEquals(new int[][]{{0, 1, 2, 2}, {0, 2, 3, 3}}, LegacyWorldRenderer.quads(LegacyVertexStream.GL_TRIANGLE_FAN, 4));
        assertArrayEquals(new int[][]{{0, 1, 3, 2}, {2, 3, 5, 4}}, LegacyWorldRenderer.quads(LegacyVertexStream.GL_QUAD_STRIP, 6));
        assertEquals(0, LegacyWorldRenderer.quads(LegacyVertexStream.GL_QUAD_STRIP, 2).length);
        assertArrayEquals(new int[][]{{0, 1, 3, 2}}, LegacyWorldRenderer.quads(LegacyVertexStream.GL_QUAD_STRIP, 5));
    }

    @Test void itemDisplayRotationAlsoRotatesLightingNormals() {
        var pose = new PoseStack();
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
        var face = List.of(vertex(0, 0, 0), vertex(1, 0, 0), vertex(1, 1, 0), vertex(0, 1, 0));
        var buffer = new BufferBuilder(1024);
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        LegacyWorldRenderer.replayStandalone(pose, buffer, List.of(new LegacyVertexStream.Primitive(LegacyVertexStream.GL_QUADS, "test", face)), 0xF000F0, 0);
        var rendered = buffer.end();
        try {
            var bytes = rendered.vertexBuffer();
            int normalOffset = DefaultVertexFormat.NEW_ENTITY.getOffset(5);
            assertEquals(127, bytes.get(normalOffset), 1); // Rotation and signed-byte packing can truncate by one.
            assertEquals(0, bytes.get(normalOffset + 1));
            assertEquals(0, bytes.get(normalOffset + 2));
        } finally { rendered.release(); }
    }
}

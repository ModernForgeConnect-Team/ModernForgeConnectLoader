package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyDisplayLists;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.bridge.LegacyWorldGraphics;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyWorldGraphicsTest {
    private static class ListDomain { }

    @Test void capturesImmediateGeometryThroughTheMatrixStack() {
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyWorldGraphics.push();
        LegacyWorldGraphics.translate(2, 0, 0);
        LegacyWorldGraphics.glBegin(LegacyVertexStream.GL_QUADS);
        LegacyWorldGraphics.glColor4f(1, 0, 0, 1);
        LegacyWorldGraphics.glNormal3f(0, 0, 1);
        addVertex(0, 0, 0); addVertex(1, 0, 0); addVertex(1, 1, 0); addVertex(0, 1, 0);
        LegacyWorldGraphics.glEnd();
        LegacyWorldGraphics.pop();
        List<LegacyVertexStream.Primitive> primitives = frame.primitives();
        frame.close();
        assertEquals(1, primitives.size());
        assertEquals(LegacyVertexStream.GL_QUADS, primitives.get(0).mode());
        assertEquals(4, primitives.get(0).vertices().size());
        LegacyVertexStream.Vertex first = primitives.get(0).vertices().get(0);
        assertEquals(2.0, first.x(), 1.0e-9);
        assertEquals(3.0, primitives.get(0).vertices().get(2).x(), 1.0e-9);
        assertEquals(0xFFFF0000, first.argb());
    }

    @Test void capturesInterleavedVertexArrays() {
        int count = 4;
        ByteBuffer bytes = ByteBuffer.allocateDirect(32 * count).order(ByteOrder.nativeOrder());
        FloatBuffer floats = bytes.asFloatBuffer();
        for (int i = 0; i < count; i++) {
            floats.put(i * 8, i).put(i * 8 + 1, 0).put(i * 8 + 2, 0).put(i * 8 + 3, 0.25f * i).put(i * 8 + 4, 0.5f);
            bytes.put(i * 32 + 20, (byte) 0x20).put(i * 32 + 21, (byte) 0x40).put(i * 32 + 22, (byte) 0x60).put(i * 32 + 23, (byte) 0xFF);
            bytes.put(i * 32 + 24, (byte) 0).put(i * 32 + 25, (byte) 0).put(i * 32 + 26, (byte) 127);
        }
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        floats.position(0); LegacyWorldGraphics.glVertexPointer(3, 32, floats); LegacyWorldGraphics.glEnableClientState(32884);
        floats.position(3); LegacyWorldGraphics.glTexCoordPointer(2, 32, floats); LegacyWorldGraphics.glEnableClientState(32888);
        bytes.position(20); LegacyWorldGraphics.glColorPointer(4, true, 32, bytes); LegacyWorldGraphics.glEnableClientState(32886);
        bytes.position(24); LegacyWorldGraphics.glNormalPointer(32, bytes); LegacyWorldGraphics.glEnableClientState(32885);
        LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_QUADS, 0, count);
        List<LegacyVertexStream.Primitive> primitives = frame.primitives();
        frame.close();
        assertEquals(1, primitives.size());
        LegacyVertexStream.Vertex third = primitives.get(0).vertices().get(2);
        assertEquals(2.0, third.x(), 1.0e-9);
        assertEquals(0.5f, third.u(), 1.0e-6);
        assertEquals(0.5f, third.v(), 1.0e-6);
        assertEquals(1.0f, third.nz(), 1.0e-6);
        assertEquals(0xFF204060, third.argb());
    }

    @Test void expandsCompiledDisplayListsIntoTheWorldCapture() {
        Class<?> owner = ListDomain.class;
        int id = LegacyDisplayLists.generate(owner, 1);
        LegacyDisplayLists.begin(owner, id, 4864);
        LegacyDisplayLists.record(owner, "glBegin(I)V", new double[]{7});
        LegacyDisplayLists.record(owner, "glVertex3d(DDD)V", new double[]{1, 2, 3});
        LegacyDisplayLists.record(owner, "glVertex3d(DDD)V", new double[]{4, 5, 6});
        LegacyDisplayLists.record(owner, "glEnd()V", new double[0]);
        LegacyDisplayLists.end(owner);
        try {
            assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.call(owner, id));
            LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
            LegacyDisplayLists.call(owner, id);
            List<LegacyVertexStream.Primitive> primitives = frame.primitives();
            frame.close();
            assertEquals(1, primitives.size());
            assertEquals(2, primitives.get(0).vertices().size());
            assertEquals(4.0, primitives.get(0).vertices().get(1).x(), 1.0e-9);
        } finally { LegacyDisplayLists.delete(owner, id, 1); }
    }

    private static void addVertex(double x, double y, double z) { LegacyWorldGraphics.glVertex3d(x, y, z); }
}

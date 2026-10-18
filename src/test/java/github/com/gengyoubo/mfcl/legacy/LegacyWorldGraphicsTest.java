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
    @Test void generatedTextureCoordinatesAndTextureMatricesDoNotMoveTheModel() {
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyWorldGraphics.glMatrixMode(5890); LegacyWorldGraphics.translate(.25,.5,0); LegacyWorldGraphics.scale(2,3,1);
            LegacyWorldGraphics.glMatrixMode(5888);
            LegacyWorldGraphics.glTexGeni(8192,9472,9217);
            LegacyWorldGraphics.glTexGen(8192,9473,FloatBuffer.wrap(new float[]{1,0,0,0}));
            LegacyWorldGraphics.glTexGen(8193,9473,FloatBuffer.wrap(new float[]{0,0,1,0}));
            LegacyWorldGraphics.glEnable(3168); LegacyWorldGraphics.glEnable(3169);
            LegacyWorldGraphics.glBegin(7); LegacyWorldGraphics.glVertex3d(.1,.2,.3); LegacyWorldGraphics.glEnd();
            var vertex = frame.primitives().get(0).vertices().get(0);
            assertEquals(.1,vertex.x(),1e-7); assertEquals(.2,vertex.y(),1e-7); assertEquals(.3,vertex.z(),1e-7);
            assertEquals(.45,vertex.u(),1e-6); assertEquals(1.4,vertex.v(),1e-6);
        }
    }
    @Test void capturesBlendAndDepthChangesPerBatchAndRestoresAttributes() {
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyWorldGraphics.glEnable(3042); LegacyWorldGraphics.glBlendFunc(770,1); LegacyWorldGraphics.glDepthMask(false);
            LegacyWorldGraphics.glPushAttrib(-1);
            LegacyWorldGraphics.glDisable(3042); LegacyWorldGraphics.glDepthMask(true);
            LegacyWorldGraphics.glBegin(7); addVertex(0,0,0); LegacyWorldGraphics.glEnd();
            LegacyWorldGraphics.glPopAttrib();
            LegacyWorldGraphics.glBegin(7); addVertex(0,0,0); LegacyWorldGraphics.glEnd();
            assertFalse(frame.primitives().get(0).state().blend()); assertTrue(frame.primitives().get(0).state().depthWrite());
            assertTrue(frame.primitives().get(1).state().blend()); assertFalse(frame.primitives().get(1).state().depthWrite());
            assertEquals(1,frame.primitives().get(1).state().destinationBlend());
        }
    }

    @Test void lightmapTextureArrayDoesNotReplaceTheBlockAtlasUvs() {
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyWorldGraphics.glVertexPointer(3, 0, FloatBuffer.wrap(new float[]{0,0,0, 1,0,0, 1,1,0, 0,1,0}));
            LegacyWorldGraphics.glTexCoordPointer(2, 0, FloatBuffer.wrap(new float[]{0,0, 1,0, 1,1, 0,1}));
            LegacyWorldGraphics.glEnableClientState(32888);
            LegacyWorldGraphics.clientActiveTexture(33985);
            LegacyWorldGraphics.glTexCoordPointer(2, 0, java.nio.ShortBuffer.wrap(new short[]{240,240, 240,240, 240,240, 240,240}));
            LegacyWorldGraphics.glEnableClientState(32888);
            LegacyWorldGraphics.clientActiveTexture(33984);
            LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_QUADS, 0, 4);
            LegacyWorldGraphics.clientActiveTexture(33985);
            LegacyWorldGraphics.glDisableClientState(32888);
            LegacyWorldGraphics.clientActiveTexture(33984);
            LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_QUADS, 0, 4);
            for (var primitive : frame.primitives()) {
                assertEquals(0f, primitive.vertices().get(0).u());
                assertEquals(1f, primitive.vertices().get(1).u());
                assertEquals(1f, primitive.vertices().get(2).v());
            }
        }
    }

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

    @Test void arrayDrawsKeepTheCurrentColorForTintedOreLayers() {
        var positions = FloatBuffer.wrap(new float[]{0,0,0, 1,0,0, 1,1,0, 0,1,0});
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyWorldGraphics.glVertexPointer(3, 0, positions);
            LegacyWorldGraphics.glEnableClientState(32884);
            LegacyWorldGraphics.glColor3f(1, 0.5f, 0);
            LegacyWorldGraphics.glNormal3f(0, 1, 0);
            LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_QUADS, 0, 4);
            LegacyWorldGraphics.glColor3f(0, 0.5f, 1);
            LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_QUADS, 0, 4);
            assertEquals(0xFFFF8000, frame.primitives().get(0).vertices().get(0).argb());
            assertEquals(0xFF0080FF, frame.primitives().get(1).vertices().get(0).argb());
            assertEquals(1f, frame.primitives().get(0).vertices().get(0).ny());
        }
    }

    @Test void cachedModelGeometryUsesTheTextureAndTintAtEachCall() {
        int id = LegacyDisplayLists.generate(ListDomain.class, 1);
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyWorldGraphics.texture("test:textures/model/first.png");
            LegacyWorldGraphics.glColor3f(1, 1, 1);
            LegacyDisplayLists.begin(ListDomain.class, id, 4864);
            LegacyWorldGraphics.glVertexPointer(3, 0, FloatBuffer.wrap(new float[]{0,0,0, 1,0,0, 1,1,0, 0,1,0}));
            LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_QUADS, 0, 4);
            LegacyDisplayLists.end(ListDomain.class);
            assertTrue(frame.primitives().isEmpty());
            LegacyWorldGraphics.texture("test:textures/model/red.png");
            LegacyWorldGraphics.glColor3f(1, 0, 0);
            LegacyDisplayLists.call(ListDomain.class, id);
            LegacyWorldGraphics.texture("test:textures/model/blue.png");
            LegacyWorldGraphics.glColor3f(0, 0, 1);
            LegacyDisplayLists.call(ListDomain.class, id);
            assertEquals("test:textures/model/red.png", frame.primitives().get(0).texture());
            assertEquals(0xFFFF0000, frame.primitives().get(0).vertices().get(0).argb());
            assertEquals("test:textures/model/blue.png", frame.primitives().get(1).texture());
            assertEquals(0xFF0000FF, frame.primitives().get(1).vertices().get(0).argb());
        } finally { LegacyDisplayLists.delete(ListDomain.class, id, 1); }
    }

    @Test void cachedModelsRetainExplicitVertexColorsInsteadOfInheritingTint() {
        int id = LegacyDisplayLists.generate(ListDomain.class, 1);
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyDisplayLists.begin(ListDomain.class, id, 4864);
            LegacyWorldGraphics.glVertexPointer(3, 0, FloatBuffer.wrap(new float[]{0,0,0}));
            LegacyWorldGraphics.glColorPointer(4, true, 0, ByteBuffer.wrap(new byte[]{0, (byte)255, 0, (byte)255}));
            LegacyWorldGraphics.glEnableClientState(32886);
            LegacyWorldGraphics.glDrawArrays(LegacyVertexStream.GL_TRIANGLES, 0, 1);
            LegacyDisplayLists.end(ListDomain.class);
            LegacyWorldGraphics.glColor3f(1, 0, 0);
            LegacyDisplayLists.call(ListDomain.class, id);
            assertEquals(0xFF00FF00, frame.primitives().get(0).vertices().get(0).argb());
        } finally { LegacyDisplayLists.delete(ListDomain.class, id, 1); }
    }

    private static void addVertex(double x, double y, double z) { LegacyWorldGraphics.glVertex3d(x, y, z); }
    @Test void capturedNormalsFollowModelRotationAndInverseScale() {
        try (var frame = LegacyWorldGraphics.begin()) {
            LegacyWorldGraphics.rotate(90, 0, 1, 0);
            LegacyWorldGraphics.glNormal3f(0, 0, 1);
            LegacyWorldGraphics.glBegin(4); addVertex(0,0,0); LegacyWorldGraphics.glEnd();
            var normal = frame.primitives().get(0).vertices().get(0);
            assertEquals(1f, normal.nx(), 1e-6f); assertEquals(0f, normal.nz(), 1e-6f);
            LegacyWorldGraphics.loadIdentity(); LegacyWorldGraphics.scale(2, 1, 1); LegacyWorldGraphics.translate(100, 200, 300);
            LegacyWorldGraphics.glNormal3f(1, 1, 0);
            LegacyWorldGraphics.glBegin(4); addVertex(0,0,0); LegacyWorldGraphics.glEnd();
            normal = frame.primitives().get(1).vertices().get(0);
            assertEquals(1 / Math.sqrt(5), normal.nx(), 1e-6);
            assertEquals(2 / Math.sqrt(5), normal.ny(), 1e-6);
        }
    }
    @Test void anInterruptedPrimitiveDoesNotPoisonTheNextItemCapture() {
        var frame = LegacyWorldGraphics.begin(); LegacyWorldGraphics.glBegin(7);
        assertThrows(IllegalStateException.class, frame::close);
        assertFalse(LegacyWorldGraphics.active());
        try (var next = LegacyWorldGraphics.begin()) { assertTrue(next.primitives().isEmpty()); }
    }
}

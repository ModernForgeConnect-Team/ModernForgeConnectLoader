package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyDisplayLists;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import github.com.gengyoubo.mfcl.bridge.LegacyWorldGraphics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LegacyDisplayListsTest {
    private static class Domain { }

    private static LegacyVertexStream.Primitive quad(String texture, double x, double y) {
        return new LegacyVertexStream.Primitive(LegacyVertexStream.GL_QUADS, texture, List.of(
                new LegacyVertexStream.Vertex(x, y, 0, 0, 0, 0, 0, 1, 0xFFFFFFFF),
                new LegacyVertexStream.Vertex(x + 1, y, 0, 1, 0, 0, 0, 1, 0xFFFFFFFF),
                new LegacyVertexStream.Vertex(x + 1, y + 1, 0, 1, 1, 0, 0, 1, 0xFFFFFFFF),
                new LegacyVertexStream.Vertex(x, y + 1, 0, 0, 1, 0, 0, 1, 0xFFFFFFFF)));
    }
    private static int compile(int id, Runnable body) {
        LegacyDisplayLists.begin(Domain.class, id, 4864);
        body.run();
        LegacyDisplayLists.end(Domain.class);
        return id;
    }

    @Test void singleQuadReplaysExactlyOnce() {
        int id = compile(1, () -> LegacyDisplayLists.geometry(quad("a", 0, 0)));
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyDisplayLists.call(Domain.class, id);
        var primitives = frame.primitives(); frame.close();
        assertEquals(1, primitives.size());
        assertEquals(4, primitives.get(0).vertices().size());
    }

    @Test void compilationDoesNotChangeTheOuterMatrix() {
        int id = compile(2, () -> {
            LegacyDisplayLists.record(Domain.class, "glTranslatef(FFF)V", new double[]{1, 0, 0});
            LegacyDisplayLists.geometry(quad("a", 0, 0));
        });
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyWorldGraphics.translate(5, 0, 0);
        LegacyDisplayLists.call(Domain.class, id); // list's translate applies during execution, not compilation
        var primitives = frame.primitives(); frame.close();
        assertEquals(1, primitives.size());
        assertEquals(6.0, primitives.get(0).vertices().get(0).x(), 1.0e-9); // outer 5 + list 1
    }

    @Test void sameListAtTwoMatricesProducesTwoPlacements() {
        int id = compile(3, () -> LegacyDisplayLists.geometry(quad("a", 0, 0)));
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyWorldGraphics.push(); LegacyWorldGraphics.translate(2, 0, 0);
        LegacyDisplayLists.call(Domain.class, id);
        LegacyWorldGraphics.pop();
        LegacyWorldGraphics.push(); LegacyWorldGraphics.translate(-2, 0, 0);
        LegacyDisplayLists.call(Domain.class, id);
        LegacyWorldGraphics.pop();
        var primitives = frame.primitives(); frame.close();
        assertEquals(2, primitives.size());
        assertEquals(2.0, primitives.get(0).vertices().get(0).x(), 1.0e-9);
        assertEquals(-2.0, primitives.get(1).vertices().get(0).x(), 1.0e-9);
    }

    @Test void listPreservesOrderOfTwoDrawBatches() {
        int id = compile(4, () -> {
            LegacyDisplayLists.record(Domain.class, "glTranslatef(FFF)V", new double[]{1, 0, 0});
            LegacyDisplayLists.geometry(quad("first", 0, 0));
            LegacyDisplayLists.record(Domain.class, "glTranslatef(FFF)V", new double[]{0, 1, 0});
            LegacyDisplayLists.geometry(quad("second", 0, 0));
        });
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyDisplayLists.call(Domain.class, id);
        var primitives = frame.primitives(); frame.close();
        assertEquals(2, primitives.size());
        assertEquals("first", primitives.get(0).texture());
        assertEquals("second", primitives.get(1).texture());
        assertEquals(1.0, primitives.get(0).vertices().get(0).x(), 1.0e-9);
        assertEquals(1.0, primitives.get(1).vertices().get(0).x(), 1.0e-9);
        assertEquals(1.0, primitives.get(1).vertices().get(0).y(), 1.0e-9); // both translates applied to the second
    }

    @Test void nestedListCallExpands() {
        int inner = compile(5, () -> LegacyDisplayLists.geometry(quad("inner", 0, 0)));
        int outer = compile(6, () -> LegacyDisplayLists.record(Domain.class, "glCallList(I)V", new double[]{inner}));
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyDisplayLists.call(Domain.class, outer);
        var primitives = frame.primitives(); frame.close();
        assertEquals(1, primitives.size());
        assertEquals("inner", primitives.get(0).texture());
    }

    @Test void deleteThenRebuildLeavesNoStaleGeometry() {
        int id = compile(7, () -> LegacyDisplayLists.geometry(quad("old", 0, 0)));
        LegacyDisplayLists.delete(Domain.class, id, 1);
        assertFalse(LegacyDisplayLists.exists(Domain.class, id));
        compile(id, () -> LegacyDisplayLists.geometry(quad("new", 0, 0)));
        LegacyWorldGraphics.Frame frame = LegacyWorldGraphics.begin();
        LegacyDisplayLists.call(Domain.class, id);
        var primitives = frame.primitives(); frame.close();
        assertEquals(1, primitives.size());
        assertEquals("new", primitives.get(0).texture());
    }

    @Test void rejectsUnsupportedExecutionAndMalformedPrimitives() {
        Class<?> owner = Malformed.class;
        assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.begin(owner, 1, 4865));
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.end(owner));
        assertFalse(LegacyDisplayLists.record(owner, "glBegin(I)V", new double[]{7}));
        LegacyDisplayLists.begin(owner, 1, 4864);
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.begin(owner, 2, 4864));
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.record(owner, "glVertex3d(DDD)V", new double[]{0, 0, 0}));
        LegacyDisplayLists.record(owner, "glBegin(I)V", new double[]{7});
        assertThrows(IllegalStateException.class, () -> LegacyDisplayLists.end(owner));
        LegacyDisplayLists.record(owner, "glEnd()V", new double[0]);
        LegacyDisplayLists.end(owner);
        assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.entries(owner, 1).clear());
        assertThrows(UnsupportedOperationException.class, () -> LegacyDisplayLists.call(owner, 1));
        LegacyDisplayLists.delete(owner, 1, 1);
        assertDoesNotThrow(() -> LegacyDisplayLists.call(owner, 1));
    }
    private static class Malformed { }
}

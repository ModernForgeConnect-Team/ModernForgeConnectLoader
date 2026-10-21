package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.bridge.LegacyIconResolver;
import github.com.gengyoubo.mfcl.bridge.LegacyVertexStream;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class LegacyIconResolverTest {
    private static List<LegacyVertexStream.Vertex> face(float left, float right) {
        return List.of(vertex(left, 0), vertex(right, 0), vertex(right, 1), vertex(left, 1));
    }
    private static LegacyVertexStream.Vertex vertex(float u, float v) {
        return new LegacyVertexStream.Vertex(0, 0, 0, u, v, 0, 0, 1, 0xFF123456);
    }
    @Test void oneTessellatorBatchCanContainFacesFromDifferentAtlasCells() {
        var vertices = new ArrayList<>(face(0, .5f)); vertices.addAll(face(.5f, 1));
        var atlas = new LegacyVertexStream.Primitive(7, "minecraft:textures/atlas/blocks.png", vertices);
        var resolved = LegacyIconResolver.resolve(List.of(atlas), Map.of("test:glass", new float[]{0,0,.5f,1}, "test:liquid", new float[]{.5f,0,1,1}));
        assertEquals(List.of("test:glass", "test:liquid"), resolved.stream().map(LegacyVertexStream.Primitive::texture).toList());
        for (var primitive : resolved) {
            assertEquals(4, primitive.vertices().size());
            assertEquals(0f, primitive.vertices().get(0).u());
            assertEquals(1f, primitive.vertices().get(1).u());
            assertEquals(0xFF123456, primitive.vertices().get(0).argb());
        }
    }
    @Test void aModelCanMixStandaloneTexturesWithAtlasFaces() {
        var model = new LegacyVertexStream.Primitive(7, "test:textures/models/brain.png", face(.125f, .25f));
        var atlas = new LegacyVertexStream.Primitive(7, "minecraft:textures/atlas/blocks.png", face(0, 1));
        var resolved = LegacyIconResolver.resolve(List.of(model, atlas), Map.of("test:jar", new float[]{0,0,1,1}));
        assertSame(model, resolved.get(0));
        assertEquals("test:jar", resolved.get(1).texture());
    }
    @Test void unknownAtlasFacesAreNotInventedFromAnUnrelatedTexture() {
        var atlas = new LegacyVertexStream.Primitive(7, "minecraft:textures/atlas/blocks.png", face(4, 5));
        assertTrue(LegacyIconResolver.resolve(List.of(atlas), Map.of("test:jar", new float[]{0,0,1,1})).isEmpty());
    }
    @Test void largeSyntheticAtlasSupportsRowsBeyondUnitUvsAndRepeatedCaptures() {
        var layout = new LinkedHashMap<String, float[]>();
        for (int i = 0; i < 44000; i++) {
            float u = i % 16 / 16f, v = i / 16 / 16f;
            layout.put("test:icon_" + i, new float[]{u, v, u + 1 / 16f, v + 1 / 16f});
        }
        var resolver = new LegacyIconResolver(layout);
        var state = new LegacyVertexStream.RenderState(true, 770, 771, false, true);
        for (int i : List.of(0, 15, 16, 17000, 43999, 0)) {
            float[] bounds = layout.get("test:icon_" + i);
            var vertices = List.of(vertex(bounds[0], bounds[1]), vertex(bounds[2], bounds[1]),
                    vertex(bounds[2], bounds[3]), vertex(bounds[0], bounds[3]));
            var resolved = resolver.resolve(List.of(new LegacyVertexStream.Primitive(7, "minecraft:textures/atlas/blocks.png", vertices, state)));
            assertEquals(1, resolved.size());
            assertEquals("test:icon_" + i, resolved.get(0).texture());
            assertEquals(state, resolved.get(0).state());
            assertEquals(List.of(0f, 1f, 1f, 0f), resolved.get(0).vertices().stream().map(LegacyVertexStream.Vertex::u).toList());
            assertEquals(List.of(0f, 0f, 1f, 1f), resolved.get(0).vertices().stream().map(LegacyVertexStream.Vertex::v).toList());
        }
    }
    @Test void overlappingCellsKeepOriginalIterationPrecedenceAcrossIndexBranches() {
        var layout = new LinkedHashMap<String, float[]>();
        layout.put("test:first", new float[]{0, 0, 10, 10});
        for (int i = 0; i < 32; i++) layout.put("test:later_" + i, new float[]{i / 4f, 0, i / 4f + 1, 1});
        var resolver = new LegacyIconResolver(layout);
        var face = new LegacyVertexStream.Primitive(7, "minecraft:textures/atlas/blocks.png", face(0, 1));
        assertEquals("test:first", resolver.resolve(List.of(face)).get(0).texture());
        assertTrue(resolver.resolve(List.of(new LegacyVertexStream.Primitive(7, face.texture(), face(20, 21)))).isEmpty());
    }
}

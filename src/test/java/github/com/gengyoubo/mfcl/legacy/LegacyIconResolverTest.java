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
}

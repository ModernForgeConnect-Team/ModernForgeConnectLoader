package github.com.gengyoubo.mfcl.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Decodes synthetic atlas UVs per face; standalone model textures already have image-local UVs. */
public final class LegacyIconResolver {
    private LegacyIconResolver() {}
    public static List<LegacyVertexStream.Primitive> resolve(List<LegacyVertexStream.Primitive> primitives, Map<String, float[]> layout) {
        var result = new ArrayList<LegacyVertexStream.Primitive>();
        for (var primitive : primitives) {
            if (primitive.texture().contains("textures/") && !primitive.texture().contains("textures/atlas/")) {
                result.add(primitive);
                continue;
            }
            int count = primitive.vertices().size();
            int faceSize = primitive.mode() == LegacyVertexStream.GL_QUADS ? 4
                    : primitive.mode() == LegacyVertexStream.GL_TRIANGLES ? 3 : count;
            if (faceSize == 0) continue;
            for (int start = 0; start + faceSize <= count; start += faceSize) {
                var face = primitive.vertices().subList(start, start + faceSize);
                float u = 0, v = 0;
                for (var vertex : face) { u += vertex.u(); v += vertex.v(); }
                u /= faceSize; v /= faceSize;
                for (var entry : layout.entrySet()) {
                    float[] cell = entry.getValue();
                    if (u < cell[0] || u > cell[2] || v < cell[1] || v > cell[3]) continue;
                    var mapped = new ArrayList<LegacyVertexStream.Vertex>(faceSize);
                    for (var vertex : face) mapped.add(new LegacyVertexStream.Vertex(vertex.x(), vertex.y(), vertex.z(),
                            (vertex.u() - cell[0]) / (cell[2] - cell[0]), (vertex.v() - cell[1]) / (cell[3] - cell[1]),
                            vertex.nx(), vertex.ny(), vertex.nz(), vertex.argb()));
                    result.add(new LegacyVertexStream.Primitive(primitive.mode(), entry.getKey(), mapped, primitive.state()));
                    break;
                }
            }
        }
        return List.copyOf(result);
    }
}

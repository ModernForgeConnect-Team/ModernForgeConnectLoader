package github.com.gengyoubo.mfcl.bridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

/** Decodes synthetic atlas UVs per face; standalone model textures already have image-local UVs. */
public final class LegacyIconResolver {
    private final Node root;

    /** Build once per atlas, then reuse for every captured block, item and effect. */
    public LegacyIconResolver(Map<String, float[]> layout) {
        var cells = new ArrayList<Cell>();
        layout.forEach((name, bounds) -> cells.add(new Cell(name, bounds.clone(), cells.size())));
        root = cells.isEmpty() ? null : new Node(cells);
    }

    private record Cell(String name, float[] bounds, int order) {}
    private static final class Node {
        final float minU, minV, maxU, maxV;
        final Node left, right;
        final List<Cell> cells;
        Node(List<Cell> entries) {
            float u0 = Float.POSITIVE_INFINITY, v0 = Float.POSITIVE_INFINITY;
            float u1 = Float.NEGATIVE_INFINITY, v1 = Float.NEGATIVE_INFINITY;
            for (var entry : entries) {
                float[] b = entry.bounds();
                u0 = Math.min(u0, b[0]); v0 = Math.min(v0, b[1]);
                u1 = Math.max(u1, b[2]); v1 = Math.max(v1, b[3]);
            }
            minU = u0; minV = v0; maxU = u1; maxV = v1;
            if (entries.size() <= 8) {
                cells = List.copyOf(entries); left = right = null;
            } else {
                int axis = maxU - minU >= maxV - minV ? 0 : 1;
                entries.sort(Comparator.comparingDouble(cell -> cell.bounds()[axis] + cell.bounds()[axis + 2]));
                int middle = entries.size() / 2;
                left = new Node(new ArrayList<>(entries.subList(0, middle)));
                right = new Node(new ArrayList<>(entries.subList(middle, entries.size())));
                cells = List.of();
            }
        }
        Cell find(float u, float v, Cell best) {
            if (u < minU || u > maxU || v < minV || v > maxV) return best;
            if (left != null) return right.find(u, v, left.find(u, v, best));
            for (var cell : cells) {
                float[] b = cell.bounds();
                if ((best == null || cell.order() < best.order()) && u >= b[0] && u <= b[2] && v >= b[1] && v <= b[3]) best = cell;
            }
            return best;
        }
    }

    public static List<LegacyVertexStream.Primitive> resolve(List<LegacyVertexStream.Primitive> primitives, Map<String, float[]> layout) {
        return new LegacyIconResolver(layout).resolve(primitives);
    }
    public List<LegacyVertexStream.Primitive> resolve(List<LegacyVertexStream.Primitive> primitives) {
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
                Cell entry = root == null ? null : root.find(u, v, null);
                if (entry != null) {
                    float[] cell = entry.bounds();
                    var mapped = new ArrayList<LegacyVertexStream.Vertex>(faceSize);
                    for (var vertex : face) mapped.add(new LegacyVertexStream.Vertex(vertex.x(), vertex.y(), vertex.z(),
                            (vertex.u() - cell[0]) / (cell[2] - cell[0]), (vertex.v() - cell[1]) / (cell[3] - cell[1]),
                            vertex.nx(), vertex.ny(), vertex.nz(), vertex.argb()));
                    result.add(new LegacyVertexStream.Primitive(primitive.mode(), entry.name(), mapped, primitive.state()));
                }
            }
        }
        return List.copyOf(result);
    }
}

package github.com.gengyoubo.mfcl.bridge;

import java.util.*;

/** Mod-neutral per-frame rendering extension. The host replays geometry/text; adapters choose dynamic items. */
public final class LegacyDynamicItems {
    public record Drawing(List<LegacyVertexStream.Primitive> primitives, List<LegacyWorldGraphics.Text> texts) {
        public Drawing { primitives = List.copyOf(primitives); texts = List.copyOf(texts); }
    }
    public interface Renderer {
        String id();
        boolean supports(String itemKey, String displayContext);
        /** Empty means use the frozen original model for this frame (e.g. no live world). */
        Optional<Drawing> capture(LegacyClientData.Stack stack, String displayContext) throws Exception;
    }
    private final Map<String, Renderer> renderers = new LinkedHashMap<>();
    public synchronized AutoCloseable register(Renderer renderer) {
        if (renderers.putIfAbsent(renderer.id(), renderer) != null) throw new IllegalArgumentException("Dynamic renderer already registered: " + renderer.id());
        return () -> { synchronized (this) { renderers.remove(renderer.id(), renderer); } };
    }
    private synchronized Renderer find(String itemKey, String context) {
        Renderer selected = null;
        for (var renderer : renderers.values()) if (renderer.supports(itemKey, context)) {
            if (selected != null) throw new IllegalStateException("Ambiguous dynamic renderers for " + itemKey + ": " + selected.id() + ", " + renderer.id());
            selected = renderer;
        }
        return selected;
    }
    public boolean supports(String itemKey, String context) { return find(itemKey, context) != null; }
    public Optional<Drawing> capture(LegacyClientData.Stack stack, String context) throws Exception {
        var renderer = find(stack.key(), context);
        return renderer == null ? Optional.empty() : Objects.requireNonNull(renderer.capture(stack, context));
    }
}

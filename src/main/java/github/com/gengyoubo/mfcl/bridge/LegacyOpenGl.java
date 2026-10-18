package github.com.gengyoubo.mfcl.bridge;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** JDK-only boundary from each private LWJGL class identity to its host rendering service. */
public final class LegacyOpenGl {
    public interface Backend extends AutoCloseable {
        Object call(String operation, Object[] arguments) throws Exception;
        default void bootstrapComplete() { }
        default Map<String, Object> statistics() { return Map.of(); }
        default Map<String, Object> verify(Class<?> gl11, Class<?> gl20) throws Exception {
            throw new UnsupportedOperationException("This rendering backend has no native verification probe");
        }
        @Override default void close() throws Exception { }
    }
    private static final ClassValue<AtomicReference<Backend>> BACKENDS = new ClassValue<>() {
        @Override protected AtomicReference<Backend> computeValue(Class<?> type) { return new AtomicReference<>(); }
    };
    private static final Object UNHANDLED = new Object();
    private static final ClassValue<CaptureTextures> CAPTURE_TEXTURES = new ClassValue<>() {
        @Override protected CaptureTextures computeValue(Class<?> type) { return new CaptureTextures(); }
    };
    private static final class CaptureTextures {
        private int next = 0x40000000, bound;
        private final Map<Integer,int[]> dimensions = new java.util.HashMap<>();
        synchronized Object call(String operation, Object[] args) {
            String name = operation.substring(0,operation.indexOf('('));
            if (name.equals("glGenTextures")) { int handle = next++; dimensions.put(handle,new int[2]); return handle; }
            if (name.equals("glBindTexture")) { bound = (int)args[1]; dimensions.putIfAbsent(bound,new int[2]); LegacyWorldGraphics.bindTexture(bound); return null; }
            if (name.equals("glDeleteTextures")) { dimensions.remove((int)args[0]); return null; }
            if (name.equals("glIsTexture")) return dimensions.containsKey((int)args[0]);
            if (name.equals("glTexImage2D")) { dimensions.put(bound,new int[]{(int)args[3],(int)args[4]}); return null; }
            if (name.equals("glGetTexLevelParameteri")) {
                var size = dimensions.getOrDefault(bound,new int[2]);
                return (int)args[2] == 4096 ? size[0] : (int)args[2] == 4097 ? size[1] : 0;
            }
            // TextureUtil.func_152777_a reads the active texture's filters and anisotropy before restoring them.
            // The capture has no native texture object, so report the filter sizes Minecraft expects to save.
            if (name.equals("glGetTexParameteri")) {
                int pname = (int)args[1];
                return pname == 10240 || pname == 10241 ? 9729 : 0; // GL_TEXTURE_MAG/MIN_FILTER -> GL_LINEAR
            }
            if (name.equals("glGetTexParameterf")) return 0f;
            if (name.equals("glGetInteger")) {
                int query = (int)args[0];
                if (query == 3379) return 16384;
                if (query == 32873) return bound;
                if (query == 3317 || query == 3333) return 4;
                if (query == 3314 || query == 3315 || query == 3316 || query == 3330 || query == 3331 || query == 3332) return 0;
            }
            if (java.util.Set.of("glTexSubImage2D","glTexParameteri","glTexParameterf","glTexEnvi","glPixelStorei","glGenerateMipmap").contains(name)) return null;
            return UNHANDLED;
        }
    }
    private LegacyOpenGl() { }
    public static void attach(Class<?> owner, Backend backend) {
        java.util.Objects.requireNonNull(backend);
        if (!BACKENDS.get(owner).compareAndSet(null, backend)) throw new IllegalStateException("Legacy GL domain already has a backend");
    }
    public static void detach(Class<?> owner) { BACKENDS.remove(owner); }
    public static Object invoke(Class<?> owner, String operation, Object[] arguments) {
        // Frame-local queries remain part of GUI capture, rather than exposing the host's matrices.
        if (operation.equals("glGetInteger(I)I")) {
            int query = (Integer) arguments[0];
            if (query == 2979 || query == 3415) return LegacyGuiGraphics.integer(query);
        }
        // A model capture must not enqueue texture allocation onto the host thread currently waiting for
        // mod construction. Geometry uses resource/icon identity; native texture objects are unnecessary here.
        if (LegacyWorldGraphics.active()) {
            Object captured = CAPTURE_TEXTURES.get(owner).call(operation,arguments);
            if (captured != UNHANDLED) return captured;
        }
        Backend backend = BACKENDS.get(owner).get();
        if (backend == null) throw new UnsupportedOperationException("No host rendering backend for " + operation);
        try { return backend.call(operation, arguments); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Host rendering call failed: " + operation, failure); }
    }
}

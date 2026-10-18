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
        if (operation.equals("glBindTexture(II)V") && LegacyWorldGraphics.active()) LegacyWorldGraphics.bindTexture((Integer) arguments[1]);
        Backend backend = BACKENDS.get(owner).get();
        if (backend == null) throw new UnsupportedOperationException("No host rendering backend for " + operation);
        try { return backend.call(operation, arguments); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Host rendering call failed: " + operation, failure); }
    }
}

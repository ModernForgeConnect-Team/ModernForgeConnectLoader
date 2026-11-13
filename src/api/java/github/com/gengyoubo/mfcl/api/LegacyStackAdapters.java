package github.com.gengyoubo.mfcl.api;

import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/** Converts native host stacks to original stacks and back, preserving the host identity in transit.
 * Adapters must return null for values they do not own, and must not modify their input.
 * The original stack must contain an adapter-specific marker sufficient to reconstruct the
 * host stack after original code changes its NBT. Never claim unmarked original stacks.
 * Calls run on the game thread and may be nested; adapters must avoid converting their own
 * marked representation recursively. Register once during mod setup. */
public final class LegacyStackAdapters {
    private static final CopyOnWriteArrayList<Adapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private LegacyStackAdapters() { }

    public interface Adapter {
        LegacyStack toLegacy(Object hostStack);
        Object toHost(LegacyStack stack);
    }

    public static AutoCloseable register(Adapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        if (!ADAPTERS.addIfAbsent(adapter)) throw new IllegalArgumentException("Adapter already registered");
        return () -> ADAPTERS.remove(adapter);
    }

    public static LegacyStack toLegacy(Object hostStack) {
        for (Adapter adapter : ADAPTERS) {
            LegacyStack result = adapter.toLegacy(hostStack);
            if (result != null) return result;
        }
        return null;
    }

    public static Object toHost(LegacyStack stack) {
        for (Adapter adapter : ADAPTERS) {
            Object result = adapter.toHost(stack);
            if (result != null) return result;
        }
        return null;
    }
}

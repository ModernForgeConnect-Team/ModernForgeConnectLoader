package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import github.com.gengyoubo.mfcl.api.spi.RuntimeProvider;
import java.util.*;
import java.util.function.Consumer;

public final class ApiRuntimeProvider implements RuntimeProvider {
    private static LegacyApiRuntime active;
    private static Map<String, LegacyModInfo> mods = Map.of();
    private static final List<Consumer<LegacyRuntime>> pending = new ArrayList<>();
    private static volatile ClassLoader resources;
    public static synchronized void installMods(Map<String, LegacyModInfo> loaded) { mods = Map.copyOf(loaded); }
    /** Original archives' class loader, used only to read assets/ resources before the gameplay API is ready. */
    public static void installResources(ClassLoader loader) { resources = loader; }
    @Override public Optional<byte[]> resource(String path) {
        var loader = resources;
        if (loader == null) return Optional.empty();
        try (var input = loader.getResourceAsStream(path)) { return input == null ? Optional.empty() : Optional.of(input.readAllBytes()); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException("Cannot read original resource " + path, failure); }
    }
    @Override public Map<String, LegacyModInfo> loadedMods() {
        synchronized (ApiRuntimeProvider.class) { return active != null && !active.isOpen() ? Map.of() : mods; }
    }
    @Override public Optional<LegacyRuntime> current() {
        synchronized (ApiRuntimeProvider.class) { return active != null && active.isOpen() ? Optional.of(active) : Optional.empty(); }
    }
    @Override public AutoCloseable whenReady(Consumer<LegacyRuntime> listener) {
        LegacyApiRuntime ready;
        synchronized (ApiRuntimeProvider.class) {
            ready = active;
            if (ready == null || !ready.isOpen()) { pending.add(listener); return () -> { synchronized (ApiRuntimeProvider.class) { pending.remove(listener); } }; }
        }
        deliver(ready, listener);
        return () -> { };
    }
    public static void publish(LegacyApiRuntime runtime) {
        List<Consumer<LegacyRuntime>> listeners;
        synchronized (ApiRuntimeProvider.class) {
            if (!runtime.isOpen()) throw new IllegalStateException("Cannot publish closed runtime");
            if (active == runtime) return;
            if (active != null && active.isOpen()) throw new IllegalStateException("A developer runtime is already published");
            active = runtime; listeners = List.copyOf(pending); pending.clear();
        }
        listeners.forEach(listener -> deliver(runtime, listener));
    }
    static synchronized void remove(LegacyApiRuntime runtime) {
        if (active == runtime) { active = null; mods = Map.of(); }
    }
    private static void deliver(LegacyApiRuntime runtime, Consumer<LegacyRuntime> listener) {
        try { listener.accept(runtime); }
        catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
        catch (Throwable failure) {
            runtime.note("READY_CALLBACK", listener.getClass().getName(), failure);
            com.mojang.logging.LogUtils.getLogger().error("MFCL developer API ready callback failed", failure);
        }
    }
}

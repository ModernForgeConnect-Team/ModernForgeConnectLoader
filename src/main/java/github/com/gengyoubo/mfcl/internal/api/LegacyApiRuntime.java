package github.com.gengyoubo.mfcl.internal.api;

import github.com.gengyoubo.mfcl.api.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.*;

/** Host implementation. Internal raw access is deliberately absent from the published API artifact. */
public final class LegacyApiRuntime implements LegacyRuntime, AutoCloseable {
    @FunctionalInterface public interface Scope { <T> T call(Callable<T> operation) throws Exception; }
    public interface Values {
        Set<String> keys(Registry registry) throws Exception;
        Object find(Registry registry, String key) throws Exception;
        Object stack(LegacyStack stack) throws Exception;
        LegacyStack snapshot(Object stack) throws Exception;
    }
    record Guard(Thread thread, BooleanSupplier alive) {
        void check() {
            if (thread != Thread.currentThread()) throw new IllegalStateException("Borrowed legacy object used on a different thread");
            if (!alive.getAsBoolean()) throw new IllegalStateException("Borrowed legacy object is no longer bound");
        }
    }
    private final class Reference implements LegacyObject {
        final Object value;
        final List<Guard> guards;
        Reference(Object value, List<Guard> guards) { this.value = value; this.guards = List.copyOf(guards); }
        LegacyApiRuntime owner() { return LegacyApiRuntime.this; }
        @Override public String typeName() { return value.getClass().getName(); }
        @Override public String toString() { return "LegacyObject[" + typeName() + "]"; }
    }
    private final ClassLoader loader;
    private final Scope scope;
    private final Values values;
    private final Set<String> mods;
    private final ArrayDeque<Diagnostic> diagnostics = new ArrayDeque<>();
    private final Conversions mappings = new Conversions();
    private volatile boolean open = true;
    public interface PlayerAccess { <T> T run(Object player, Function<LegacyObject, T> operation); }
    private PlayerAccess playerAccess;
    private LegacyHostRepresentations hostRepresentations;

    public void installHostRepresentations(LegacyHostRepresentations representations) {
        this.hostRepresentations = Objects.requireNonNull(representations);
    }
    @Override public LegacyHostRepresentations hostRepresentations() {
        checkOpen();
        return Objects.requireNonNull(hostRepresentations, "Host representations are unavailable");
    }

    public void installPlayerAccess(PlayerAccess access) { this.playerAccess = Objects.requireNonNull(access); }
    @Override public <T> T withPlayer(Object hostPlayer, Function<LegacyObject, T> operation) {
        Objects.requireNonNull(hostPlayer); Objects.requireNonNull(operation);
        return call("PLAYER_ACTION", "withPlayer", () -> {
            if (playerAccess == null) throw new UnsupportedOperationException("Player access is unavailable");
            return playerAccess.run(hostPlayer, operation);
        });
    }

    public LegacyApiRuntime(ClassLoader loader, Scope scope, Values values, Set<String> mods) {
        this.loader = Objects.requireNonNull(loader); this.scope = Objects.requireNonNull(scope);
        this.values = Objects.requireNonNull(values); this.mods = Set.copyOf(mods);
    }
    ClassLoader loader() { return loader; }
    boolean owns(Class<?> type) {
        for (ClassLoader domain = loader; domain != null && domain != ClassLoader.getPlatformClassLoader(); domain = domain.getParent())
            if (type.getClassLoader() == domain) return true;
        return false;
    }
    private void checkOpen() { if (!open) throw new IllegalStateException("Legacy runtime is closed"); }
    @Override public boolean isOpen() { return open; }
    @Override public String sourceVersion() { return "1.7.10"; }
    @Override public Set<String> loadedMods() { return mods; }
    @Override public <T> T accessor(Class<T> contract) { return accessor(contract, null); }
    @Override public <T> T accessor(Class<T> contract, LegacyObject receiver) {
        return call("MLA_BIND", contract.getName(), () -> new AccessorEngine(this).bind(contract, receiver));
    }
    @Override public Set<String> registryKeys(Registry registry) {
        return call("REGISTRY", registry.name(), () -> Set.copyOf(values.keys(registry)));
    }
    @Override public Optional<LegacyObject> find(Registry registry, String key) {
        return call("REGISTRY", registry + ":" + key, () -> Optional.ofNullable(reference(values.find(registry, key))));
    }
    @Override public LegacyObject stack(LegacyStack stack) {
        return call("STACK_IMPORT", stack.key(), () -> Objects.requireNonNull(reference(values.stack(stack)), "Missing legacy stack"));
    }
    @Override public LegacyStack snapshot(LegacyObject stack) {
        return call("STACK_EXPORT", stack.typeName(), () -> values.snapshot(raw(stack)));
    }
    @Override public LegacyMappings mappings() { checkOpen(); return mappings; }
    @Override public synchronized List<Diagnostic> diagnostics() { return List.copyOf(diagnostics); }
    public synchronized void note(String code, String operation, Throwable failure) {
        while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
        if (diagnostics.size() == 64) diagnostics.removeFirst();
        diagnostics.addLast(new Diagnostic(code, operation, failure.getClass().getName() + ": " + failure.getMessage()));
    }
    <T> T call(String code, String operation, Callable<T> work) {
        try { return scope.call(() -> { checkOpen(); return work.call(); }); }
        catch (VirtualMachineError | ThreadDeath fatal) { throw fatal; }
        catch (Throwable failure) {
            if (failure instanceof LegacyAccessException access) throw access;
            while (failure instanceof InvocationTargetException && failure.getCause() != null) failure = failure.getCause();
            if (failure instanceof VirtualMachineError fatal) throw fatal;
            if (failure instanceof ThreadDeath fatal) throw fatal;
            note(code, operation, failure);
            throw new LegacyAccessException(code, operation, failure);
        }
    }
    public LegacyObject reference(Object old) { return old == null ? null : new Reference(old, List.of()); }
    public LegacyObject borrowed(Object old, BooleanSupplier alive) {
        return old == null ? null : new Reference(old, List.of(new Guard(Thread.currentThread(), alive)));
    }
    public Object raw(LegacyObject object) {
        checkOpen();
        if (!(object instanceof LegacyApiRuntime.Reference ref) || ref.owner() != this)
            throw new IllegalArgumentException("Reference belongs to a different runtime or was not created by MFCL");
        ref.guards.forEach(Guard::check);
        return ref.value;
    }
    void collectGuards(Object object, List<Guard> guards) {
        LegacyObject reference = referenceOf(object);
        if (reference != null) { raw(reference); guards.addAll(((Reference) reference).guards); }
        else if (object instanceof Object[] array) for (Object entry : array) collectGuards(entry, guards);
    }
    private LegacyObject referenceOf(Object value) {
        if (value instanceof LegacyObject reference) return reference;
        if (value != null && Proxy.isProxyClass(value.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(value);
            if (handler instanceof AccessorEngine.Handler accessor) return accessor.reference();
        }
        return null;
    }
    Object argument(Object value, Class<?> expected) {
        if (value == null) {
            if (expected.isPrimitive()) throw new IllegalArgumentException("Null primitive argument");
            return null;
        }
        LegacyObject reference = referenceOf(value);
        if (reference != null) {
            Object old = raw(reference);
            if (!expected.isInstance(old)) throw new IllegalArgumentException("Wrong legacy argument type: " + expected.getName());
            return old;
        }
        if (expected.isArray()) {
            int size = Array.getLength(value); Object copy = Array.newInstance(expected.getComponentType(), size);
            for (int i = 0; i < size; i++) Array.set(copy, i, argument(Array.get(value, i), expected.getComponentType()));
            return copy;
        }
        if (expected.isPrimitive() || expected == String.class && value instanceof String) return value;
        throw new IllegalArgumentException("Legacy reference required: " + expected.getName());
    }
    Object result(Object value, Class<?> apiType, List<Guard> guards) {
        if (value == null || apiType == void.class) return null;
        LegacyObject known = referenceOf(value);
        if (known != null) {
            if (apiType == LegacyObject.class) return known;
            if (apiType.isInterface() && apiType.isAnnotationPresent(github.com.gengyoubo.mfcl.api.mla.LegacyType.class)) {
                if (apiType.isInstance(value)) return value;
                return wrap(apiType, known);
            }
            return value;
        }
        if (apiType == LegacyObject.class) return new Reference(value, guards);
        if (apiType.isArray()) {
            int size = Array.getLength(value); Object copy = Array.newInstance(apiType.getComponentType(), size);
            for (int i = 0; i < size; i++) Array.set(copy, i, result(Array.get(value, i), apiType.getComponentType(), guards));
            return copy;
        }
        if (apiType.isInterface() && apiType.isAnnotationPresent(github.com.gengyoubo.mfcl.api.mla.LegacyType.class))
            return wrap(apiType, new Reference(value, guards));
        return value; // signature was checked at binding: only exact primitives and String
    }
    private <T> T wrap(Class<T> apiType, LegacyObject reference) {
        try { return new AccessorEngine(this).bind(apiType, reference); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Cannot wrap legacy value as " + apiType.getName(), failure); }
    }
    @Override public void close() {
        try { scope.call(() -> { open = false; synchronized (mappings) { mappings.entries.clear(); } return null; }); }
        catch (Exception failure) { throw new IllegalStateException("Cannot close developer API", failure); }
        ApiRuntimeProvider.remove(this);
    }

    private final class Conversions implements LegacyMappings {
        private record Entry<H>(Class<H> type, Function<H, LegacyObject> toLegacy, Function<LegacyObject, H> toHost) {
            LegacyObject from(Object host) { return toLegacy.apply(type.cast(host)); }
            Object to(LegacyObject old) { return toHost.apply(old); }
        }
        private final Map<String, Entry<?>> entries = new LinkedHashMap<>();
        private synchronized Entry<?> entry(String key) {
            checkOpen();
            return Objects.requireNonNull(entries.get(key), "Unsupported mapping: " + key);
        }
        @Override public synchronized Set<String> supported() { checkOpen(); return Set.copyOf(entries.keySet()); }
        @Override public Optional<LegacyObject> toLegacy(String mapping, Object hostValue) {
            return call("MAPPING_IMPORT", mapping, () -> {
                LegacyObject old = entry(mapping).from(Objects.requireNonNull(hostValue));
                if (old != null) raw(old);
                return Optional.ofNullable(old);
            });
        }
        @Override public <H> Optional<H> toHost(String mapping, LegacyObject old, Class<H> hostType) {
            return call("MAPPING_EXPORT", mapping, () -> {
                raw(old);
                var entry = entry(mapping);
                if (!hostType.isAssignableFrom(entry.type)) throw new IllegalArgumentException("Wrong host type for " + mapping);
                return Optional.ofNullable(hostType.cast(entry.to(old)));
            });
        }
        @Override public synchronized <H> AutoCloseable register(String key, Class<H> hostType,
                Function<H, LegacyObject> toLegacy, Function<LegacyObject, H> toHost) {
            checkOpen();
            if (!key.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) throw new IllegalArgumentException("Expected namespaced mapping key");
            var entry = new Entry<>(Objects.requireNonNull(hostType), Objects.requireNonNull(toLegacy), Objects.requireNonNull(toHost));
            if (entries.putIfAbsent(key, entry) != null) throw new IllegalArgumentException("Mapping already registered: " + key);
            return () -> { synchronized (this) { entries.remove(key, entry); } };
        }
    }
}

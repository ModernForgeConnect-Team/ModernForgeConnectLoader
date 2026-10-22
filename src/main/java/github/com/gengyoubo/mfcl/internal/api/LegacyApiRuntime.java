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

    public interface HostBlocks { Object state(String legacyKey, int metadata); }
    private HostBlocks hostBlocks;
    public void installHostBlocks(HostBlocks blocks) { this.hostBlocks = Objects.requireNonNull(blocks); }
    @Override public <H> Optional<H> hostBlock(String legacyKey, int metadata, Class<H> hostType) {
        Objects.requireNonNull(legacyKey); Objects.requireNonNull(hostType);
        return call("HOST_BLOCK", legacyKey + "@" + metadata, () -> {
            if (hostBlocks == null) throw new UnsupportedOperationException("Host block states are unavailable");
            Object state = hostBlocks.state(legacyKey, metadata);
            if (state != null && !hostType.isInstance(state)) throw new IllegalArgumentException("Wrong host type for block state: " + hostType.getName());
            return Optional.ofNullable(hostType.cast(state));
        });
    }

    private LegacyLanguages languages;
    public void installLanguages(LegacyLanguages languages) { this.languages = Objects.requireNonNull(languages); }
    @Override public LegacyLanguages languages() {
        checkOpen();
        return Objects.requireNonNull(languages, "Legacy languages are unavailable");
    }

    private final Set<String> features = Collections.synchronizedSet(new TreeSet<>(Set.of(
            LegacyFeatures.LANGUAGES, LegacyFeatures.MLA_ARRAYS, LegacyFeatures.MLA_COLLECTIONS, LegacyFeatures.MLA_COLLECTIONS_MUTABLE,
            LegacyFeatures.MLA_INSTANCE, LegacyFeatures.MLA_OBJECT_ARGUMENTS)));
    /** Host installation only; the set is fixed once the runtime is published. */
    public void addFeatures(String... names) { features.addAll(List.of(names)); }
    @Override public Set<String> features() { return Set.copyOf(features); }

    private PlayerAccess clientPlayerAccess;
    public void installClientPlayerAccess(PlayerAccess access) { this.clientPlayerAccess = Objects.requireNonNull(access); }
    @Override public <T> T withClientPlayer(Function<LegacyObject, T> operation) {
        Objects.requireNonNull(operation);
        return call("CLIENT_PLAYER", "withClientPlayer", () -> {
            if (clientPlayerAccess == null) throw new UnsupportedOperationException("Client player access is unavailable");
            return clientPlayerAccess.run(null, operation);
        });
    }
    private LegacyEquipment equipment;
    public void installEquipment(LegacyEquipment value) { this.equipment = Objects.requireNonNull(value); }
    @Override public LegacyEquipment equipment() {
        checkOpen();
        if (equipment == null) throw new UnsupportedOperationException("Equipment sources are unavailable");
        return equipment;
    }
    private LegacyEvents events;
    public void installEvents(LegacyEvents value) { this.events = Objects.requireNonNull(value); }
    @Override public LegacyEvents events() {
        checkOpen();
        if (events == null) throw new UnsupportedOperationException("Event forwarding is unavailable");
        return events;
    }
    private final LegacyValues valueAccess = new ValueAccess();
    @Override public LegacyValues values() { checkOpen(); return valueAccess; }

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
    /** Host merge for {@link LegacyMappings#applyTo}; the mapping key itself is registered separately. */
    public void installMerge(String key, BiFunction<Object, LegacyObject, Object> merge) { mappings.merge(key, Objects.requireNonNull(merge)); }
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
    /** Old value as API callers see it: null, String and boxed primitives as themselves, anything else a reference. */
    public Object exposed(Object old) { return exposed(old, List.of()); }
    private Object exposed(Object old, List<Guard> guards) {
        return old == null || plain(old) ? old : new Reference(old, guards);
    }
    /** Inverse of {@link #exposed}: references and accessor proxies become their old object. */
    public Object unexposed(Object value) {
        LegacyObject reference = referenceOf(value);
        return reference == null ? value : raw(reference);
    }
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
        if (value.getClass().isArray()) {
            // A host array is copied into an old one; under an Object parameter its elements stay Object (primitive arrays as they are).
            Class<?> source = value.getClass().getComponentType();
            Class<?> component = expected.isArray() ? expected.getComponentType() : source.isPrimitive() ? source : Object.class;
            int size = Array.getLength(value); Object copy = Array.newInstance(component, size);
            if (!expected.isInstance(copy)) throw new IllegalArgumentException("Wrong legacy argument type: " + expected.getName());
            for (int i = 0; i < size; i++) Array.set(copy, i, argument(Array.get(value, i), component));
            return copy;
        }
        if (expected.isPrimitive()) return value;
        // Strings and boxed values cross as themselves wherever the old parameter accepts them (Object, Object[] elements...).
        if (plain(value) && expected.isInstance(value)) return value;
        throw new IllegalArgumentException("Legacy reference required: " + expected.getName());
    }
    static boolean plain(Object value) {
        return value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof Character;
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
        if (apiType == Object.class) return exposed(value, guards);
        if (apiType.isArray()) {
            int size = Array.getLength(value); Object copy = Array.newInstance(apiType.getComponentType(), size);
            for (int i = 0; i < size; i++) Array.set(copy, i, result(Array.get(value, i), apiType.getComponentType(), guards));
            return copy;
        }
        if (apiType.isInterface() && apiType.isAnnotationPresent(github.com.gengyoubo.mfcl.api.mla.LegacyType.class))
            return wrap(apiType, new Reference(value, guards));
        return value; // signature was checked at binding: only exact primitives, String and boxed types
    }
    private <T> T wrap(Class<T> apiType, LegacyObject reference) {
        try { return new AccessorEngine(this).bind(apiType, reference); }
        catch (RuntimeException failure) { throw failure; }
        catch (Exception failure) { throw new IllegalStateException("Cannot wrap legacy value as " + apiType.getName(), failure); }
    }
    @Override public void close() {
        try { scope.call(() -> { open = false; synchronized (mappings) { mappings.entries.clear(); mappings.merges.clear(); } return null; }); }
        catch (Exception failure) { throw new IllegalStateException("Cannot close developer API", failure); }
        ApiRuntimeProvider.remove(this);
    }

    private final class ValueAccess implements LegacyValues {
        private Object array(LegacyObject array) {
            Object value = raw(Objects.requireNonNull(array));
            if (!value.getClass().isArray()) throw new IllegalArgumentException("Not an old array: " + value.getClass().getName());
            return value;
        }
        private List<Guard> guards(LegacyObject source) { var guards = new ArrayList<Guard>(); collectGuards(source, guards); return guards; }
        @Override public int length(LegacyObject array) { return call("MLA_ARRAY", "length", () -> Array.getLength(array(array))); }
        @Override public Object get(LegacyObject array, int index) {
            return call("MLA_ARRAY", "get[" + index + "]", () -> exposed(Array.get(array(array), index), guards(array)));
        }
        @Override public void set(LegacyObject array, int index, Object value) {
            call("MLA_ARRAY", "set[" + index + "]", () -> {
                Object target = array(array);
                Array.set(target, index, element(value, target.getClass().getComponentType()));
                return null;
            });
        }
        /** An API value as an old element of {@code type}: the same rules as accessor arguments. */
        private Object element(Object value, Class<?> type) { return argument(value, type); }
        @Override public LegacyObject newArray(String componentType, int length) {
            Objects.requireNonNull(componentType);
            return call("MLA_ARRAY", "new " + componentType + "[" + length + "]", () -> {
                if (length < 0) throw new NegativeArraySizeException(String.valueOf(length));
                Class<?> component = switch (componentType) {
                    case "boolean" -> boolean.class; case "byte" -> byte.class; case "char" -> char.class; case "short" -> short.class;
                    case "int" -> int.class; case "long" -> long.class; case "float" -> float.class; case "double" -> double.class;
                    default -> Class.forName(componentType, false, loader);
                };
                return reference(Array.newInstance(component, length));
            });
        }
        private Collection<Object> collection(LegacyObject source) {
            Object value = raw(Objects.requireNonNull(source));
            if (!(value instanceof Collection<?> collection)) throw new IllegalArgumentException("Not an old Collection: " + value.getClass().getName());
            @SuppressWarnings("unchecked") var result = (Collection<Object>) collection;
            return result;
        }
        private Map<Object, Object> oldMap(LegacyObject source) {
            Object value = raw(Objects.requireNonNull(source));
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Not an old Map: " + value.getClass().getName());
            @SuppressWarnings("unchecked") var result = (Map<Object, Object>) map;
            return result;
        }
        @Override public int size(LegacyObject source) {
            return call("MLA_COLLECTION", "size", () -> {
                Object value = raw(Objects.requireNonNull(source));
                if (value.getClass().isArray()) return Array.getLength(value);
                if (value instanceof Collection<?> collection) return collection.size();
                if (value instanceof Map<?, ?> map) return map.size();
                throw new IllegalArgumentException("Not an old array, Collection or Map: " + value.getClass().getName());
            });
        }
        @Override public LegacyObject newList(Iterable<?> elements) {
            Objects.requireNonNull(elements);
            return call("MLA_COLLECTION", "newList", () -> {
                var list = new ArrayList<Object>();
                for (Object element : elements) list.add(element(element, Object.class));
                return reference(list);
            });
        }
        @Override public LegacyObject newMap(Map<?, ?> entries) {
            Objects.requireNonNull(entries);
            return call("MLA_COLLECTION", "newMap", () -> {
                var map = new LinkedHashMap<Object, Object>();
                for (var entry : entries.entrySet()) map.put(element(entry.getKey(), Object.class), element(entry.getValue(), Object.class));
                return reference(map);
            });
        }
        @Override public boolean add(LegacyObject collection, Object value) {
            return call("MLA_COLLECTION", "add", () -> collection(collection).add(element(value, Object.class)));
        }
        @Override public boolean remove(LegacyObject collection, Object value) {
            return call("MLA_COLLECTION", "remove", () -> collection(collection).remove(element(value, Object.class)));
        }
        @Override public int removeIf(LegacyObject collection, java.util.function.Predicate<Object> filter) {
            Objects.requireNonNull(filter);
            return call("MLA_COLLECTION", "removeIf", () -> {
                var target = collection(collection); var guards = guards(collection); int removed = 0;
                for (var iterator = target.iterator(); iterator.hasNext(); )
                    if (filter.test(exposed(iterator.next(), guards))) { iterator.remove(); removed++; }
                return removed;
            });
        }
        @Override public Object put(LegacyObject map, Object key, Object value) {
            return call("MLA_COLLECTION", "put", () -> exposed(oldMap(map).put(element(key, Object.class), element(value, Object.class)), guards(map)));
        }
        @Override public Object removeKey(LegacyObject map, Object key) {
            return call("MLA_COLLECTION", "removeKey", () -> exposed(oldMap(map).remove(element(key, Object.class)), guards(map)));
        }
        @Override public int removeEntries(LegacyObject map, java.util.function.BiPredicate<Object, Object> filter) {
            Objects.requireNonNull(filter);
            return call("MLA_COLLECTION", "removeEntries", () -> {
                var target = oldMap(map); var guards = guards(map); int removed = 0;
                for (var iterator = target.entrySet().iterator(); iterator.hasNext(); ) {
                    var entry = iterator.next();
                    if (filter.test(exposed(entry.getKey(), guards), exposed(entry.getValue(), guards))) { iterator.remove(); removed++; }
                }
                return removed;
            });
        }
        @Override public void clear(LegacyObject source) {
            call("MLA_COLLECTION", "clear", () -> {
                Object value = raw(Objects.requireNonNull(source));
                if (value instanceof Collection<?> collection) collection.clear();
                else if (value instanceof Map<?, ?> map) map.clear();
                else throw new IllegalArgumentException("Not an old Collection or Map: " + value.getClass().getName());
                return null;
            });
        }
        @Override public boolean same(Object left, Object right) {
            return call("MLA_INSTANCE", "same", () -> {
                LegacyObject a = referenceOf(left), b = referenceOf(right);
                if (a == null || b == null) throw new IllegalArgumentException("Expected legacy references or accessor proxies");
                return raw(a) == raw(b);
            });
        }
        @Override public List<Object> list(LegacyObject source) {
            return call("MLA_COLLECTION", "list", () -> {
                Object value = raw(Objects.requireNonNull(source)); var guards = guards(source); var result = new ArrayList<Object>();
                if (value.getClass().isArray()) for (int i = 0, n = Array.getLength(value); i < n; i++) result.add(exposed(Array.get(value, i), guards));
                else if (value instanceof Iterable<?> iterable) for (Object entry : iterable) result.add(exposed(entry, guards));
                else throw new IllegalArgumentException("Not an old Iterable or array: " + value.getClass().getName());
                return Collections.unmodifiableList(result);
            });
        }
        @Override public Map<Object, Object> map(LegacyObject source) {
            return call("MLA_COLLECTION", "map", () -> {
                Object value = raw(Objects.requireNonNull(source)); var guards = guards(source);
                if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Not an old Map: " + value.getClass().getName());
                // References compare by identity, so keys stay distinct exactly as the original map's entries.
                var result = new LinkedHashMap<Object, Object>();
                for (var entry : map.entrySet()) result.put(exposed(entry.getKey(), guards), exposed(entry.getValue(), guards));
                return Collections.unmodifiableMap(result);
            });
        }
        @Override public boolean isInstance(LegacyObject value, String legacyType) {
            Objects.requireNonNull(legacyType);
            return call("MLA_INSTANCE", legacyType, () -> {
                Object old = raw(Objects.requireNonNull(value));
                try { return Class.forName(legacyType, false, loader).isInstance(old); }
                catch (ClassNotFoundException absent) { return false; }
            });
        }
        @Override public boolean isInstance(LegacyObject value, Class<?> contract) {
            var annotation = contract.getAnnotation(github.com.gengyoubo.mfcl.api.mla.LegacyType.class);
            if (annotation == null) throw new IllegalArgumentException("Expected a @LegacyType contract: " + contract.getName());
            return isInstance(value, annotation.value());
        }
        @Override public <T> Optional<T> as(LegacyObject value, Class<T> contract) {
            return isInstance(value, contract) ? Optional.of(accessor(contract, value)) : Optional.empty();
        }
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
        private final Map<String, BiFunction<Object, LegacyObject, Object>> merges = new HashMap<>();
        synchronized void merge(String key, BiFunction<Object, LegacyObject, Object> merge) { merges.put(key, merge); }
        @Override public <H> H applyTo(String mapping, H existing, LegacyObject old) {
            Objects.requireNonNull(existing);
            return call("MAPPING_EXPORT", mapping, () -> {
                if (old != null) raw(old);
                var entry = entry(mapping);
                BiFunction<Object, LegacyObject, Object> merge;
                synchronized (this) { merge = merges.get(mapping); }
                if (merge == null) throw new UnsupportedOperationException("Mapping cannot merge into host values: " + mapping);
                if (!entry.type.isInstance(existing)) throw new IllegalArgumentException("Wrong host type for " + mapping);
                @SuppressWarnings("unchecked") H result = (H) entry.type.cast(merge.apply(existing, old));
                return result;
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

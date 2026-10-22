package github.com.gengyoubo.mfcl.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.Predicate;

/**
 * Operations MLA contracts cannot express: old arrays in place, JDK collections the old runtime owns, and type tests.
 *
 * <p>Values cross as follows. Out of the old runtime: {@code null}, {@code String} and boxed primitives
 * ({@code Number}, {@code Boolean}, {@code Character}) as themselves, anything else as a {@link LegacyObject} that
 * shares the source reference's thread and lifetime guards. Into it: {@code null}, a {@code String} or boxed value
 * (wherever the target type accepts it: a primitive array, an {@code Object[]}, a {@code List}...), a
 * {@link LegacyObject}, or a {@code @LegacyType} accessor proxy. Call on the thread that owns the references (as for
 * accessors); old collections are not thread-safe.
 */
public interface LegacyValues {
    /** Length of an old array of any component type. */
    int length(LegacyObject array);

    /** One element of an old array. */
    Object get(LegacyObject array, int index);

    /** Writes one element in place; the value must fit the array's component type (a boxed value for a primitive array). */
    void set(LegacyObject array, int index, Object value);

    /**
     * {@link LegacyFeatures#MLA_ARRAYS}. A new old array of {@code length}. {@code componentType} is a primitive name
     * ({@code "int"}), a class name in the isolated runtime ({@code "net.minecraft.item.ItemStack"},
     * {@code "java.lang.Object"}) or a JVM array name ({@code "[Lnet.minecraft.item.ItemStack;"}).
     */
    LegacyObject newArray(String componentType, int length);

    /** {@link #newArray} filled with {@code elements}. */
    default LegacyObject array(String componentType, Object... elements) {
        var array = newArray(componentType, elements.length);
        for (int i = 0; i < elements.length; i++) set(array, i, elements[i]);
        return array;
    }

    /**
     * Snapshot of an old {@code Iterable} (lists, sets, a {@code Map}'s key set...) or array, in iteration order.
     * The returned list is immutable and unaffected by later changes.
     */
    List<Object> list(LegacyObject iterableOrArray);

    /** Snapshot of an old {@code java.util.Map}, in iteration order. */
    Map<Object, Object> map(LegacyObject map);

    /** Elements of an old array, {@code Collection} or {@code Map}. */
    int size(LegacyObject arrayCollectionOrMap);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. A new old {@code java.util.ArrayList} holding {@code elements}. */
    LegacyObject newList(Iterable<?> elements);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. A new old {@code java.util.LinkedHashMap} (a {@code HashMap}) holding {@code entries}. */
    LegacyObject newMap(Map<?, ?> entries);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. {@code Collection.add} on an old collection. */
    boolean add(LegacyObject collection, Object value);

    /**
     * {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. {@code Collection.remove} on an old collection; old objects
     * compare with their own {@code equals} (identity for most, such as ItemStack and recipes).
     */
    boolean remove(LegacyObject collection, Object value);

    /**
     * {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. Removes the elements {@code filter} accepts from an old
     * collection (for example original recipes from {@code CraftingManager} or {@code ThaumcraftApi} lists) and
     * returns how many. The filter sees elements as described above; they stay valid while it runs.
     */
    int removeIf(LegacyObject collection, Predicate<Object> filter);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. {@code Map.put}; returns the previous value. */
    Object put(LegacyObject map, Object key, Object value);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. {@code Map.remove}; returns the previous value. */
    Object removeKey(LegacyObject map, Object key);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. Removes the entries {@code filter} accepts from an old map and returns how many. */
    int removeEntries(LegacyObject map, BiPredicate<Object, Object> filter);

    /** {@link LegacyFeatures#MLA_COLLECTIONS_MUTABLE}. Empties an old collection or map. */
    void clear(LegacyObject collectionOrMap);

    /** Whether both references (or accessor proxies) denote the very same old object. References are not interned, so {@code ==} cannot tell. */
    boolean same(Object left, Object right);

    /** Old {@code instanceof} against a class or interface name in the isolated runtime, e.g. {@code "baubles.api.IBauble"}. False when absent. */
    boolean isInstance(LegacyObject value, String legacyType);

    /** {@link #isInstance(LegacyObject, String)} against a {@code @LegacyType} contract's target. */
    boolean isInstance(LegacyObject value, Class<?> contract);

    /** Binds {@code contract} to {@code value} when it is an instance of the contract's target. */
    <T> Optional<T> as(LegacyObject value, Class<T> contract);
}

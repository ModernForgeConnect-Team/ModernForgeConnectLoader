package github.com.gengyoubo.mfcl.api;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Operations MLA contracts cannot express: old arrays in place, read-only snapshots of JDK collections the old
 * runtime owns, and type tests. Element values cross as follows: {@code null}, {@code String} and boxed primitives as
 * themselves, anything else as a {@link LegacyObject} that shares the source reference's thread and lifetime guards.
 * Call on the thread that owns the references (as for accessors).
 */
public interface LegacyValues {
    /** Length of an old array of any component type. */
    int length(LegacyObject array);

    /** One element of an old array, converted as described above. */
    Object get(LegacyObject array, int index);

    /**
     * Writes one element in place. {@code value} is null, a {@code String}, a boxed primitive for a primitive array,
     * a {@link LegacyObject}, or a {@code @LegacyType} accessor proxy; it must fit the array's component type.
     */
    void set(LegacyObject array, int index, Object value);

    /**
     * Snapshot of an old {@code Iterable} (lists, sets, a {@code Map}'s key set...) or array, in iteration order.
     * The returned list is immutable and unaffected by later changes; elements are converted as described above.
     */
    List<Object> list(LegacyObject iterableOrArray);

    /** Snapshot of an old {@code java.util.Map}, in iteration order; keys and values are converted as described above. */
    Map<Object, Object> map(LegacyObject map);

    /** Old {@code instanceof} against a class or interface name in the isolated runtime, e.g. {@code "baubles.api.IBauble"}. False when absent. */
    boolean isInstance(LegacyObject value, String legacyType);

    /** {@link #isInstance(LegacyObject, String)} against a {@code @LegacyType} contract's target. */
    boolean isInstance(LegacyObject value, Class<?> contract);

    /** Binds {@code contract} to {@code value} when it is an instance of the contract's target. */
    <T> Optional<T> as(LegacyObject value, Class<T> contract);
}

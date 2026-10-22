package github.com.gengyoubo.mfcl.api;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Explicit, named conversions. Missing representations return empty; malformed or lossy inputs fail. */
public interface LegacyMappings {
    String ITEM_STACK = "mfcl:item_stack";
    String WORLD = "mfcl:world";
    Set<String> supported();
    Optional<LegacyObject> toLegacy(String mapping, Object hostValue);
    <H> Optional<H> toHost(String mapping, LegacyObject legacyValue, Class<H> hostType);
    /**
     * {@link LegacyFeatures#MAPPINGS_APPLY}, {@link #ITEM_STACK} only. Converts {@code legacyValue} like
     * {@link #toHost} but keeps what only the host knows from {@code existingHostValue}: for item stacks the
     * host-owned tags ({@code Enchantments}, {@code RepairCost}, {@code display}) and the stack's capability data
     * (Curios and similar), as long as the item is unchanged. Returns a new host value; {@code existingHostValue}
     * is not modified. A null or used-up {@code legacyValue} gives the host's empty value.
     */
    default <H> H applyTo(String mapping, H existingHostValue, LegacyObject legacyValue) {
        throw new UnsupportedOperationException("This MFCL build does not merge host values");
    }
    /** Adapter extension point. Keys cannot replace an existing converter. Null means no representation. */
    <H> AutoCloseable register(String key, Class<H> hostType,
                              Function<H, LegacyObject> toLegacy, Function<LegacyObject, H> toHost);
}

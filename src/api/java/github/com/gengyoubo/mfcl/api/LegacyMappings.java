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
    /** Adapter extension point. Keys cannot replace an existing converter. Null means no representation. */
    <H> AutoCloseable register(String key, Class<H> hostType,
                              Function<H, LegacyObject> toLegacy, Function<LegacyObject, H> toHost);
}

package github.com.gengyoubo.mfcl.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Experimental API v2. Call gameplay members on the appropriate logical game thread. Members marked with a feature
 * throw {@link UnsupportedOperationException} exactly when {@link #features()} does not report that feature.
 */
public interface LegacyRuntime {
    enum Registry { ITEM, BLOCK }
    record Diagnostic(String code, String operation, String detail) { }
    boolean isOpen();
    String sourceVersion();
    Set<String> loadedMods();
    /** {@link LegacyFeatures} names this runtime supports, fixed once the runtime is published. Empty before API v2. */
    default Set<String> features() { return Set.of(); }
    default boolean supports(String feature) { return features().contains(feature); }
    <T> T accessor(Class<T> contract);
    <T> T accessor(Class<T> contract, LegacyObject receiver);
    Set<String> registryKeys(Registry registry);
    Optional<LegacyObject> find(Registry registry, String key);
    LegacyObject stack(LegacyStack stack);
    LegacyStack snapshot(LegacyObject stack);
    LegacyMappings mappings();
    /** {@link LegacyFeatures#HOST_REPRESENTATIONS}. */
    default LegacyHostRepresentations hostRepresentations() {
        throw new UnsupportedOperationException("This MFCL build does not provide host registry representations");
    }
    /**
     * {@link LegacyFeatures#HOST_BLOCKS}. Host block state that mirrors an original block at a metadata value, for
     * example to place original ores from host world generation. Empty when the original block has no host
     * representation. The returned state is a plain host value and may be cached and used off the game thread.
     */
    default <H> Optional<H> hostBlock(String legacyKey, int metadata, Class<H> hostType) {
        throw new UnsupportedOperationException("This MFCL build does not provide host block states");
    }
    /** {@link LegacyFeatures#LANGUAGES}. Fallback translations for keys the original legacy language files leave untranslated. */
    default LegacyLanguages languages() {
        throw new UnsupportedOperationException("This MFCL build does not provide legacy language supplements");
    }
    /**
     * {@link LegacyFeatures#PLAYER}. Runs on the owning server thread with the real player's legacy knowledge loaded.
     * The old player is the same persistent object for the same host player and world on every call, carrying the host
     * entity id ({@link LegacyFeatures#PLAYER_IDENTITY}) and current equipment. Successful changes are persisted and
     * old packets synchronized; equipment and main-inventory changes are written back to the host; failures restore
     * prior entity data. The reference and references derived from it expire when the callback returns. Return
     * snapshots only.
     */
    default <T> T withPlayer(Object hostPlayer, java.util.function.Function<LegacyObject, T> operation) {
        throw new UnsupportedOperationException("This MFCL build does not provide scoped player access");
    }
    /**
     * {@link LegacyFeatures#CLIENT_PLAYER}. Runs on the client thread with the old client player: the very object old
     * {@code Minecraft.thePlayer} and {@code renderViewEntity} return, refreshed from the host local player. Changes
     * are not persisted or written back (the server is authoritative). The reference expires when the callback returns.
     * Throws {@link IllegalStateException} off the client thread or without a local player.
     */
    default <T> T withClientPlayer(java.util.function.Function<LegacyObject, T> operation) {
        throw new UnsupportedOperationException("This MFCL build does not provide client player access");
    }
    /** {@link LegacyFeatures#EQUIPMENT}. */
    default LegacyEquipment equipment() {
        throw new UnsupportedOperationException("This MFCL build does not provide equipment sources");
    }
    /** {@link LegacyFeatures#EVENTS}. */
    default LegacyEvents events() {
        throw new UnsupportedOperationException("This MFCL build does not forward host events");
    }
    /** {@link LegacyFeatures#MLA_ARRAYS}, {@link LegacyFeatures#MLA_COLLECTIONS}, {@link LegacyFeatures#MLA_INSTANCE}. */
    default LegacyValues values() {
        throw new UnsupportedOperationException("This MFCL build does not provide legacy value access");
    }
    /** Bounded failure history; values contain no legacy objects. */
    List<Diagnostic> diagnostics();
}

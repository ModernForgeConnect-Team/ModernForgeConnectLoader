package github.com.gengyoubo.mfcl.api;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Experimental API v1. Call gameplay members on the appropriate logical game thread. */
public interface LegacyRuntime {
    enum Registry { ITEM, BLOCK }
    record Diagnostic(String code, String operation, String detail) { }
    boolean isOpen();
    String sourceVersion();
    Set<String> loadedMods();
    <T> T accessor(Class<T> contract);
    <T> T accessor(Class<T> contract, LegacyObject receiver);
    Set<String> registryKeys(Registry registry);
    Optional<LegacyObject> find(Registry registry, String key);
    LegacyObject stack(LegacyStack stack);
    LegacyStack snapshot(LegacyObject stack);
    LegacyMappings mappings();
    default LegacyHostRepresentations hostRepresentations() {
        throw new UnsupportedOperationException("This MFCL build does not provide host registry representations");
    }
    /**
     * Host block state that mirrors an original block at a metadata value, for example to place original ores
     * from host world generation. Empty when the original block has no host representation. The returned state
     * is a plain host value and may be cached and used off the game thread.
     */
    default <H> Optional<H> hostBlock(String legacyKey, int metadata, Class<H> hostType) {
        throw new UnsupportedOperationException("This MFCL build does not provide host block states");
    }
    /** Fallback translations for keys the original legacy language files leave untranslated. */
    default LegacyLanguages languages() {
        throw new UnsupportedOperationException("This MFCL build does not provide legacy language supplements");
    }
    /**
     * Runs on the owning server thread with the real player's legacy knowledge loaded.
     * Successful changes are persisted and old packets synchronized; failures restore prior data.
     * The player and references derived from it expire when the callback returns. Return snapshots only.
     */
    default <T> T withPlayer(Object hostPlayer, java.util.function.Function<LegacyObject, T> operation) {
        throw new UnsupportedOperationException("This MFCL build does not provide scoped player access");
    }
    /** Bounded failure history; values contain no legacy objects. */
    List<Diagnostic> diagnostics();
}

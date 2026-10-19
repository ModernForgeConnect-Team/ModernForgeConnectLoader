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
    /** Bounded failure history; values contain no legacy objects. */
    List<Diagnostic> diagnostics();
}

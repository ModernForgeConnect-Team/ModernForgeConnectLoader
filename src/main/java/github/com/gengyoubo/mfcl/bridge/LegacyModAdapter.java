package github.com.gengyoubo.mfcl.bridge;

import java.util.Map;
import java.util.Set;

/** Optional mod integration. Loader/FML and game object semantics do not depend on any implementation. */
public interface LegacyModAdapter {
    String id();
    boolean supports(Set<String> modIds);
    default void beforeBoot(ClassLoader legacyLoader) throws Exception {}
    Map<String, Object> initialize(LegacyServices services) throws Exception;
    default void installClient() {}
    /** For {@link #beforeBoot}: the legacy loader applies these legacy-side class transformers by name. */
    static void registerBytecodeAdapters(ClassLoader legacyLoader, String... adapters) throws Exception {
        var register = legacyLoader.getClass().getMethod("registerBytecodeAdapter", String.class);
        for (String adapter : adapters) register.invoke(legacyLoader, adapter);
    }
}

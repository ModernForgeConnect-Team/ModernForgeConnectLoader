package github.com.gengyoubo.mfcl.api;

import github.com.gengyoubo.mfcl.api.spi.RuntimeProvider;
import java.util.Optional;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/** Host-only entry point. The API artifact contains no Minecraft or Forge classes. */
public final class MfclApi {
    public static final int API_VERSION = 1;
    private MfclApi() { }
    private static final class Provider {
        static final Optional<RuntimeProvider> INSTANCE = load();
        private static Optional<RuntimeProvider> load() {
            var providers = ServiceLoader.load(RuntimeProvider.class, MfclApi.class.getClassLoader()).iterator();
            if (!providers.hasNext()) return Optional.empty();
            var provider = providers.next();
            if (providers.hasNext()) throw new IllegalStateException("Multiple MFCL runtime providers");
            return Optional.of(provider);
        }
    }
    public static Optional<LegacyRuntime> runtime() {
        return Provider.INSTANCE.flatMap(RuntimeProvider::current);
    }
    /**
     * Loaded original mods, keyed by case-sensitive legacy ID (for example, "Thaumcraft").
     * Query from common setup or later; before MFCL finishes construction this is empty.
     * This metadata does not require gameplay API readiness and never includes merely discovered JARs.
     */
    public static Map<String, LegacyModInfo> loadedMods() {
        return Provider.INSTANCE.map(RuntimeProvider::loadedMods).orElseGet(Map::of);
    }
    public static Optional<LegacyModInfo> mod(String id) {
        return Optional.ofNullable(loadedMods().get(java.util.Objects.requireNonNull(id)));
    }
    public static boolean isModLoaded(String id) {
        return mod(id).isPresent();
    }
    /**
     * Reads an {@code assets/...} file from the loaded original mods or the original game, for example
     * {@code "assets/thaumcraft/textures/blocks/cinnibar.png"}. Available from mod construction (so resource-pack
     * finders can derive host assets at runtime instead of redistributing them); empty when absent or not loaded.
     */
    public static Optional<byte[]> legacyResource(String path) {
        java.util.Objects.requireNonNull(path);
        if (!path.startsWith("assets/") || path.contains("..") || path.contains("\\"))
            throw new IllegalArgumentException("Expected a relative assets/ path: " + path);
        return Provider.INSTANCE.flatMap(p -> p.resource(path));
    }
    /** Called once on the host setup thread, or immediately if already ready. Closing cancels a pending callback. */
    public static AutoCloseable whenReady(Consumer<LegacyRuntime> listener) {
        java.util.Objects.requireNonNull(listener);
        return Provider.INSTANCE.map(p -> p.whenReady(listener)).orElseGet(() -> () -> { });
    }
}

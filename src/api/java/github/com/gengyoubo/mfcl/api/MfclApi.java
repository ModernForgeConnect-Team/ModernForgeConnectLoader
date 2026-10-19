package github.com.gengyoubo.mfcl.api;

import github.com.gengyoubo.mfcl.api.spi.RuntimeProvider;
import java.util.Optional;
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
    /** Called once on the host setup thread, or immediately if already ready. Closing cancels a pending callback. */
    public static AutoCloseable whenReady(Consumer<LegacyRuntime> listener) {
        java.util.Objects.requireNonNull(listener);
        return Provider.INSTANCE.map(p -> p.whenReady(listener)).orElseGet(() -> () -> { });
    }
}

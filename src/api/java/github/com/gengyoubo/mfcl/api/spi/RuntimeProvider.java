package github.com.gengyoubo.mfcl.api.spi;

import github.com.gengyoubo.mfcl.api.LegacyRuntime;
import java.util.Optional;
import java.util.function.Consumer;

/** Implemented by the loader, not by an addon. Addons should use MfclApi. */
public interface RuntimeProvider {
    Optional<LegacyRuntime> current();
    AutoCloseable whenReady(Consumer<LegacyRuntime> listener);
}

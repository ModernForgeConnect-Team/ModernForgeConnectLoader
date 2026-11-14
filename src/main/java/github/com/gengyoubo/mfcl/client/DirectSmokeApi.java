package github.com.gengyoubo.mfcl.client;

/**
 * Host contract of the API smoke test's direct class ({@code src/directSmoke}), packaged into
 * {@code META-INF/mfcl/direct/} only for {@code -PmfclApiSmokeTest} runs.
 */
public interface DirectSmokeApi {
    /** Original display name of the old player's helmet, or "" without one. */
    String helmetName(Object legacyPlayer);
    /** The old player's helmet stack itself. */
    Object helmet(Object legacyPlayer);
    /** Loader class names of the direct class and of the colliding name {@code net.minecraft.server.MinecraftServer}. */
    String loaders();
    /** Size of Thaumcraft's original aspect registry, read through its unobfuscated API. */
    int aspectCount();
    /** Original CommandBase.compareTo over two direct subclasses ("alpha" vs "beta"): negative when overrides are reobfuscated. */
    int originalCallsOverride();
}

package github.com.gengyoubo.mfcl.api;

/**
 * Last-resort call-site redirection inside original classes, for hard-coded checks no other API reaches (for example
 * another mod testing {@code armorItemInSlot(3) instanceof X}). Only available to a
 * {@link github.com.gengyoubo.mfcl.api.spi.LegacyRedirectProvider}, before the old runtime loads its classes.
 * Optional: when unsupported ({@link LegacyFeatures#REDIRECTS} absent) providers are not called and the original code runs.
 */
public interface LegacyRedirects {
    /**
     * Rewrites calls to {@code owner.name descriptor} (exact call-site owner, SRG name and JVM descriptor, as the
     * compiled original references it) in old classes whose name starts with {@code callers} (a class name or a
     * package prefix ending in {@code '.'}). MFCL, JDK and launch classes are never rewritten. Several redirects of the
     * same call chain: the latest registered runs first and its {@link Call#proceed} reaches the previous one.
     */
    void redirect(String callers, String owner, String name, String descriptor, Handler handler);

    interface Handler {
        /**
         * Runs instead of the call, on the thread executing the original code, usually inside MFCL's runtime lock.
         * Returns the call's result: null for void, a boxed primitive, a {@code String}, or a {@link LegacyObject}
         * (or {@code @LegacyType} proxy) of a type the descriptor accepts. Calls made before the developer runtime
         * is ready run the original target without the handler.
         */
        Object handle(Call call) throws Throwable;
    }

    /** Valid only while the handler runs. */
    interface Call {
        /** The class containing the rewritten call site. */
        String caller();
        /** Receiver of an instance call; null for a static call. */
        LegacyObject receiver();
        /** Arguments converted like {@link LegacyValues} elements; a fresh copy on every call. */
        Object[] arguments();
        /** Invokes the original target (or the previously registered redirect) with these arguments. */
        Object proceed(Object... arguments) throws Throwable;
    }
}

package github.com.gengyoubo.mfcl.bridge;

/**
 * Old client code tried to change authoritative world state. 1.7.10 applied such writes as a local prediction that
 * the server then overwrote; here the client defers to the server's update instead.
 */
public final class LegacyClientMutationException extends UnsupportedOperationException {
    public LegacyClientMutationException(String message) { super(message); }
    /** Whether {@code failure} (possibly wrapped by reflection) was caused by such a write. */
    public static boolean causes(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) if (current instanceof LegacyClientMutationException) return true;
        return false;
    }
}

package github.com.gengyoubo.mfcl.bridge;

/** A host block/state has no faithful old representation; read-only renderers may omit its scan overlay. */
public final class UnmappedLegacyBlockException extends UnsupportedOperationException {
    public UnmappedLegacyBlockException(String message) {super(message);}
    public static UnmappedLegacyBlockException find(Throwable failure) {
        for(Throwable cause=failure;cause!=null;cause=cause.getCause())
            if(cause instanceof UnmappedLegacyBlockException unmapped)return unmapped;
        return null;
    }
}

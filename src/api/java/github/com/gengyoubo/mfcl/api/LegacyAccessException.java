package github.com.gengyoubo.mfcl.api;

public final class LegacyAccessException extends IllegalStateException {
    private final String code;
    public LegacyAccessException(String code, String operation, Throwable cause) {
        super(code + ": " + operation, cause);
        this.code = code;
    }
    public String code() { return code; }
}

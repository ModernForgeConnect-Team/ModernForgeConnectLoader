package github.com.gengyoubo.mfcl.legacy;

public record LegacyDiagnostic(boolean error, String code, String message) {
    public static LegacyDiagnostic error(String code, String message) { return new LegacyDiagnostic(true, code, message); }
    public static LegacyDiagnostic notice(String code, String message) { return new LegacyDiagnostic(false, code, message); }
}

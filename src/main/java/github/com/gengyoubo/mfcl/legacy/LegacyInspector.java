package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.GsonBuilder;
import java.nio.file.Path;

public final class LegacyInspector {
    private LegacyInspector() {}

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Usage: LegacyInspector <game-directory>");
        var report = new LegacyRuntimeService().inspect(Path.of(args[0]));
        System.out.println(new GsonBuilder().setPrettyPrinting().create().toJson(report));
        if (report.diagnostics().stream().anyMatch(LegacyDiagnostic::error)) System.exit(1);
    }
}

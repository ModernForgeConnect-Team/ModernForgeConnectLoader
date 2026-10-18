package github.com.gengyoubo.mfcl.legacy;

import java.nio.file.Path;

public final class LegacyInspector {
    private LegacyInspector() {}

    public static void main(String[] args) {
        if (args.length != 1) throw new IllegalArgumentException("Usage: LegacyInspector <game-directory>");
        var report = new LegacyRuntimeService().inspect(Path.of(args[0]));
        System.out.println("MFCL inspection: " + report.state());
        System.out.println("Legacy directory: " + report.modDirectory());
        for (var archive : report.archives()) {
            System.out.println(Path.of(archive.file()).getFileName() + " sha256=" + archive.sha256()
                    + " mods=" + archive.mods().stream().map(LegacyMod::id).toList() + " corePlugin=" + archive.corePlugin());
        }
        for (var diagnostic : report.diagnostics()) System.out.println(diagnostic.code() + ": " + diagnostic.message());
        System.out.println("Full report: " + report.reportFile());
        if (report.diagnostics().stream().anyMatch(LegacyDiagnostic::error)) System.exit(1);
    }
}

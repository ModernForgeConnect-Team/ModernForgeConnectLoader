package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

public final class LegacyRuntimeService {
    public enum State { NO_LEGACY_MODS, DEPENDENCY_ERRORS, RUNTIME_NOT_READY }

    public record Report(String targetMinecraft, String sourceMinecraft, String modDirectory, String reportFile,
                         State state, boolean legacyFmlStarted, boolean legacyClientReady,
                         List<LegacyArchive> archives, List<LegacyDiagnostic> diagnostics) {
        public Report { archives = List.copyOf(archives); diagnostics = List.copyOf(diagnostics); }
    }

    public Report inspect(Path gameDirectory) {
        LegacyPaths paths = new LegacyPaths(gameDirectory);
        List<LegacyDiagnostic> diagnostics = new ArrayList<>();
        List<LegacyArchive> archives = List.of();
        try {
            var discovery = new LegacyDiscovery().scan(paths);
            archives = discovery.archives();
            diagnostics.addAll(discovery.diagnostics());
            diagnostics.addAll(new LegacyDependencyValidator().validate(archives));
        } catch (IOException failure) {
            diagnostics.add(LegacyDiagnostic.error("LEGACY_DIRECTORY_IO", paths.modDirectory() + ": " + failure.getMessage()));
        }
        State state = diagnostics.stream().anyMatch(LegacyDiagnostic::error) ? State.DEPENDENCY_ERRORS
                : archives.isEmpty() ? State.NO_LEGACY_MODS : State.RUNTIME_NOT_READY;
        if (state == State.RUNTIME_NOT_READY) {
            diagnostics.add(LegacyDiagnostic.notice("LEGACY_RUNTIME_NOT_READY",
                    "Legacy archives were inspected. Embedded Forge 1.7.10 lifecycle and NEI client rendering are not yet available; these mods are not running."));
        }
        Report report = new Report("1.20.1", "1.7.10", paths.modDirectory().toString(), paths.reportFile().toString(),
                state, false, false, archives, diagnostics);
        try { writeReport(paths.reportFile(), report); }
        catch (IOException failure) {
            diagnostics.add(LegacyDiagnostic.error("REPORT_WRITE_FAILED", failure.getMessage()));
            report = new Report(report.targetMinecraft(), report.sourceMinecraft(), report.modDirectory(), report.reportFile(),
                    State.DEPENDENCY_ERRORS, false, false, archives, diagnostics);
        }
        return report;
    }

    private static void writeReport(Path target, Report report) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "legacy-1.7.10-", ".json.tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}

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
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;

public final class LegacyRuntimeService {
    public enum State { NO_LEGACY_MODS, DEPENDENCY_ERRORS, RUNTIME_NOT_READY, RUNTIME_FAILED, FML_AVAILABLE, CLIENT_DATA_AVAILABLE }
    private EmbeddedLegacyRuntime runtime;
    private java.util.Set<String> loadedModIds = java.util.Set.of();
    public EmbeddedLegacyRuntime runtime() { return runtime; }
    public java.util.Set<String> loadedModIds() { return loadedModIds; }

    public Report start(Path gameDirectory, Path runtimeDirectory) {
        Report inspected = inspect(gameDirectory);
        if (inspected.state() != State.RUNTIME_NOT_READY) return inspected;
        List<LegacyDiagnostic> diagnostics = new ArrayList<>(inspected.diagnostics());
        diagnostics.removeIf(d -> d.code().equals("LEGACY_RUNTIME_NOT_READY"));
        boolean started = false, clientDataReady = false;
        State state = State.RUNTIME_NOT_READY;
        try {
            if (!Files.isRegularFile(runtimeDirectory.resolve("minecraft-forge-1.7.10-srg.jar"))) {
                diagnostics.add(LegacyDiagnostic.notice("RUNTIME_INPUT_MISSING", "Prepare the private Forge 1.7.10 runtime in " + runtimeDirectory));
            } else {
                runtime = new EmbeddedLegacyRuntime(gameDirectory, runtimeDirectory, inspected);
                var stages = runtime.boot();
                if (stages.get("loadedMods") instanceof List<?> loaded) {
                    loadedModIds = loaded.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
                }
                writeReport(gameDirectory.resolve("logs/mfcl/runtime-1.7.10.json"), stages);
                started = Boolean.TRUE.equals(stages.get("legacyFmlStarted"));
                clientDataReady = started && stages.get("adapters") instanceof java.util.Map<?, ?> reports
                        && !reports.isEmpty() && reports.values().stream().allMatch(v -> v instanceof java.util.Map<?, ?> details && "READY".equals(details.get("status")));
                state = clientDataReady ? State.CLIENT_DATA_AVAILABLE : started ? State.FML_AVAILABLE : State.RUNTIME_FAILED;
                if (!started) diagnostics.add(LegacyDiagnostic.error("LEGACY_BOOT_FAILED", stages.get("stage") + ": " + stages.get("failure")));
                else if (!clientDataReady) diagnostics.add(LegacyDiagnostic.notice("LEGACY_CLIENT_DATA_FAILED", String.valueOf(stages.get("adapters"))));
            }
        } catch (Throwable failure) {
            state = State.RUNTIME_FAILED;
            diagnostics.add(LegacyDiagnostic.error("LEGACY_BOOT_FAILED", failure.toString()));
        }
        Report result = new Report(inspected.targetMinecraft(), inspected.sourceMinecraft(), inspected.modDirectory(), inspected.reportFile(),
                state, started, false, inspected.archives(), diagnostics);
        try { writeReport(Path.of(result.reportFile()), result); }
        catch (IOException error) { throw new IllegalStateException("Cannot save legacy boot report", error); }
        return result;
    }

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
                    "Legacy archives were inspected only. This inspection did not start the embedded runtime; see runtime-1.7.10.json for an actual boot result."));
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

    private static void writeReport(Path target, Object report) throws IOException {
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "legacy-1.7.10-", ".json.tmp");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report), StandardCharsets.UTF_8);
            try { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
    }
}

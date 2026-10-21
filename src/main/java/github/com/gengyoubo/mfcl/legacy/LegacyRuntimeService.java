package github.com.gengyoubo.mfcl.legacy;

import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import github.com.gengyoubo.mfcl.legacy.runtime.EmbeddedLegacyRuntime;

public final class LegacyRuntimeService {
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    public enum State { NO_LEGACY_MODS, DEPENDENCY_ERRORS, RUNTIME_NOT_READY, RUNTIME_FAILED, FML_AVAILABLE, CLIENT_DATA_AVAILABLE }
    private EmbeddedLegacyRuntime runtime;
    private java.util.Set<String> loadedModIds = java.util.Set.of();
    private java.util.Map<String, String> loadedModVersions = java.util.Map.of();
    public EmbeddedLegacyRuntime runtime() { return runtime; }
    public java.util.Set<String> loadedModIds() { return loadedModIds; }
    public java.util.Map<String, String> loadedModVersions() { return loadedModVersions; }
    private String autoPrepareFailure;

    /**
     * Downloads and prepares the private runtime when legacy mods need it and it is missing or broken; call before
     * {@link #start}. A failure (for example offline) is kept and reported by the startup check instead of thrown.
     */
    public void prepareRuntime(Path gameDirectory, Path runtimeDirectory, java.util.function.Consumer<String> progress) {
        autoPrepareFailure = github.com.gengyoubo.mfcl.legacy.runtime.RuntimeInstaller.prepareIfNeeded(gameDirectory, runtimeDirectory, progress);
    }

    public Report start(Path gameDirectory, Path runtimeDirectory) {
        return start(gameDirectory, runtimeDirectory, null);
    }
    public Report start(Path gameDirectory, Path runtimeDirectory, github.com.gengyoubo.mfcl.bridge.LegacyOpenGl.Backend backend) {
        return start(gameDirectory, runtimeDirectory, backend, github.com.gengyoubo.mfcl.bridge.LegacyClientData.ClientSession.offline());
    }
    public Report start(Path gameDirectory, Path runtimeDirectory, github.com.gengyoubo.mfcl.bridge.LegacyOpenGl.Backend backend,
                        github.com.gengyoubo.mfcl.bridge.LegacyClientData.ClientSession session) {
        loadedModIds = java.util.Set.of();
        loadedModVersions = java.util.Map.of();
        Report inspected = inspect(gameDirectory);
        // Checked on every startup, also without legacy mods (then only as notices), so a broken runtime is visible early.
        List<LegacyDiagnostic> runtimeCheck = LegacyRuntimeCheck.run(gameDirectory, runtimeDirectory, inspected.archives(), autoPrepareFailure);
        if (inspected.state() != State.RUNTIME_NOT_READY) {
            if (runtimeCheck.isEmpty()) return inspected;
            List<LegacyDiagnostic> diagnostics = new ArrayList<>(inspected.diagnostics());
            diagnostics.addAll(runtimeCheck);
            Report checked = new Report(inspected.targetMinecraft(), inspected.sourceMinecraft(), inspected.modDirectory(), inspected.reportFile(),
                    inspected.state(), false, false, inspected.archives(), diagnostics);
            try { writeReport(Path.of(checked.reportFile()), checked); }
            catch (IOException error) { throw new IllegalStateException("Cannot save legacy boot report", error); }
            return checked;
        }
        List<LegacyDiagnostic> diagnostics = new ArrayList<>(inspected.diagnostics());
        diagnostics.removeIf(d -> d.code().equals("LEGACY_RUNTIME_NOT_READY"));
        boolean started = false, clientDataReady = false;
        State state = State.RUNTIME_NOT_READY;
        try {
            diagnostics.addAll(runtimeCheck);
            if (runtimeCheck.stream().anyMatch(LegacyDiagnostic::error)) {
                // Not a quiet notice: every discovered legacy mod is skipped, so make it a runtime failure that the
                // log, the reports and the client UI all surface.
                state = State.RUNTIME_FAILED;
            } else {
                runtime = new EmbeddedLegacyRuntime(gameDirectory, runtimeDirectory, inspected);
                runtime.clientSession(session);
                runtime.openGlBackend(backend);
                var stages = runtime.boot();
                if (stages.get("loadedMods") instanceof List<?> loaded) {
                    loadedModIds = loaded.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
                }
                if (stages.get("loadedModVersions") instanceof java.util.Map<?, ?> versions) {
                    var values = new java.util.HashMap<String, String>();
                    versions.forEach((id, version) -> values.put(String.valueOf(id), version == null ? "" : String.valueOf(version)));
                    loadedModVersions = java.util.Map.copyOf(values);
                }
                writeReport(gameDirectory.resolve("logs/mfcl/runtime-1.7.10.json"), stages);
                started = Boolean.TRUE.equals(stages.get("legacyFmlStarted"));
                clientDataReady = started && stages.get("adapters") instanceof java.util.Map<?, ?> reports
                        && !reports.isEmpty() && reports.values().stream().allMatch(v -> v instanceof java.util.Map<?, ?> details && "READY".equals(details.get("status")));
                state = clientDataReady ? State.CLIENT_DATA_AVAILABLE : started ? State.FML_AVAILABLE : State.RUNTIME_FAILED;
                if (!started) {
                    diagnostics.add(LegacyDiagnostic.error("LEGACY_BOOT_FAILED", stages.get("stage") + ": "
                            + stages.get("failure") + (stages.containsKey("rootCause") ? " / " + stages.get("rootCause") : "")));
                    LOGGER.error("MFCL legacy runtime failed at {}:\n{}", stages.get("stage"), stages.get("stackTrace"));
                }
                else if (!clientDataReady && stages.get("adapters") instanceof java.util.Map<?, ?> reports && !reports.isEmpty())
                    diagnostics.add(LegacyDiagnostic.notice("LEGACY_CLIENT_DATA_FAILED", String.valueOf(reports)));
            }
        } catch (Throwable failure) {
            state = State.RUNTIME_FAILED;
            LOGGER.error("MFCL legacy runtime startup failed", failure);
            Throwable cause = failure;
            var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Throwable, Boolean>());
            while (cause instanceof InvocationTargetException && cause.getCause() != null && seen.add(cause)) cause = cause.getCause();
            Throwable rootCause = cause;
            seen.clear();
            while (rootCause.getCause() != null && seen.add(rootCause)) rootCause = rootCause.getCause();
            String summary = cause.toString() + (rootCause != cause ? " / " + rootCause : "");
            diagnostics.add(LegacyDiagnostic.error("LEGACY_BOOT_FAILED", summary));
            var trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            var details = new java.util.LinkedHashMap<String, Object>();
            details.put("stage", "EMBEDDED_RUNTIME");
            details.put("legacyFmlStarted", started);
            details.put("failure", cause.toString());
            details.put("rootCause", rootCause.toString());
            details.put("stackTrace", trace.toString());
            try { writeReport(gameDirectory.resolve("logs/mfcl/runtime-1.7.10.json"), details); }
            catch (IOException reportFailure) {
                LOGGER.error("Cannot save legacy runtime failure report", reportFailure);
                diagnostics.add(LegacyDiagnostic.error("REPORT_WRITE_FAILED", reportFailure.toString()));
            }
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

package github.com.gengyoubo.mfcl.legacy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Immutable view shared by the mod list and both loading counters. */
public record LegacyModCatalog(String targetMinecraft, String sourceMinecraft, List<Entry> entries, String failure) {
    private static final Set<String> RUNTIME_COMPONENTS = Set.of("mcp", "FML", "Forge");
    private static final Pattern COMPLETION = Pattern.compile("Mod loading complete - (\\d+) mods loaded");

    public record Entry(LegacyMod mod, String file, boolean loaded, Path archive) {
        public Entry(LegacyMod mod, String file, boolean loaded) { this(mod, file, loaded, null); }
    }

    public LegacyModCatalog { entries = List.copyOf(entries); }

    public static LegacyModCatalog from(LegacyRuntimeService.Report report, Set<String> loadedIds) {
        var entries = new LinkedHashMap<String, Entry>();
        Set<String> successful = report.legacyFmlStarted() ? Set.copyOf(loadedIds) : Set.of();
        for (var archive : report.archives()) for (var mod : archive.mods()) {
            if (!RUNTIME_COMPONENTS.contains(mod.id())) entries.putIfAbsent(mod.id(),
                    new Entry(mod, Path.of(archive.file()).getFileName().toString(), successful.contains(mod.id()), Path.of(archive.file())));
        }
        // Coremods can supply containers which have no statically discoverable @Mod or metadata.
        successful.stream().filter(id -> !RUNTIME_COMPONENTS.contains(id)).sorted().forEach(id ->
                entries.putIfAbsent(id, new Entry(new LegacyMod(id, id, "", report.sourceMinecraft(),
                        "", "", List.of()), "(runtime)", true)));
        boolean failed = report.state() == LegacyRuntimeService.State.RUNTIME_FAILED || report.state() == LegacyRuntimeService.State.DEPENDENCY_ERRORS;
        String failure = failed ? report.diagnostics().stream().filter(LegacyDiagnostic::error).map(LegacyDiagnostic::message)
                .findFirst().orElse(report.state().name()) : "";
        return new LegacyModCatalog(report.targetMinecraft(), report.sourceMinecraft(), List.copyOf(entries.values()), failure);
    }

    public int loadedCount() { return (int) entries.stream().filter(Entry::loaded).count(); }
    /** Runtime container versions take precedence over discovery metadata, including dynamic coremods. */
    public Map<String, github.com.gengyoubo.mfcl.api.LegacyModInfo> loadedMods(Map<String, String> versions) {
        var result = new LinkedHashMap<String, github.com.gengyoubo.mfcl.api.LegacyModInfo>();
        for (var entry : entries) if (entry.loaded()) {
            var mod = entry.mod();
            result.put(mod.id(), new github.com.gengyoubo.mfcl.api.LegacyModInfo(mod.id(), mod.name(),
                    versions.getOrDefault(mod.id(), mod.version()), sourceMinecraft));
        }
        return Map.copyOf(result);
    }
    public int totalLoaded(int modernCount) { return Math.addExact(modernCount, loadedCount()); }

    /** Preserve every other Forge status, especially errors. Only count completed legacy FML mods. */
    public String completionMessage(String original) {
        var match = COMPLETION.matcher(original);
        return loadedCount() > 0 && match.matches()
                ? "Mod loading complete - " + totalLoaded(Integer.parseInt(match.group(1))) + " mods loaded"
                : original;
    }
}

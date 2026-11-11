package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.api.LegacyModInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.language.IConfigurable;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks modern addons' mfclDependencies tables against successfully loaded original mods. */
public final class LegacyAddonDependencies {
    private LegacyAddonDependencies() { }

    public static List<String> validateFile(Set<String> owners, IConfigurable config,
                                            Map<String, LegacyModInfo> loaded, Dist dist, Context context) {
        var errors = new ArrayList<String>();
        try {
            Object declarations = config.getConfigElement("mfclDependencies").orElse(null);
            if (declarations == null) return List.of();
            if (!(declarations instanceof Map<?, ?> tables))
                throw new IllegalArgumentException("expected [[mfclDependencies.<modern-mod-id>]] tables");
            for (Object owner : tables.keySet()) if (!owners.contains(owner))
                errors.add("mfclDependencies refers to unknown modern mod ID " + owner + " in this file");
            for (String owner : owners) {
                int index = 0;
                for (var entry : config.getConfigList("mfclDependencies", owner)) {
                    String location = "mfclDependencies." + owner + "[" + index++ + "]";
                    try {
                        var requirement = Requirement.read(entry);
                        String failure = requirement.check(loaded, dist, context);
                        if (failure != null) errors.add(location + ": " + failure);
                    } catch (IllegalArgumentException invalid) {
                        errors.add(location + ": " + invalid.getMessage());
                    }
                }
            }
        } catch (RuntimeException invalid) {
            errors.add("mfclDependencies for " + owners + ": " + invalid.getMessage());
        }
        return List.copyOf(errors);
    }

    /**
     * Why an original mod may be absent. Legacy FML loads all-or-nothing, so a problem in any 1.7.10 mod makes every
     * requirement fail; the addon-facing line says which case applies and {@link #explain} names the actual blockers once.
     */
    public record Context(boolean runtimeStarted, LegacyRuntimeService.State state, List<LegacyDiagnostic> blockers,
                          Map<String, Found> discovered, String scanned, String bootReport, String runtimeReport) {
        public record Found(String id, String name, String version, String file) { }
        private static final int MAX_BLOCKERS = 20;

        public static Context of(LegacyRuntimeService.Report report, Path gameDirectory) {
            var found = new LinkedHashMap<String, Found>();
            for (var archive : report.archives()) for (var mod : archive.mods())
                found.putIfAbsent(mod.id(), new Found(mod.id(), mod.name(), mod.version(), Path.of(archive.file()).getFileName().toString()));
            var paths = new LegacyPaths(gameDirectory);
            return new Context(report.legacyFmlStarted(), report.state(),
                    report.diagnostics().stream().filter(LegacyDiagnostic::error).toList(), Map.copyOf(found),
                    paths.rootModDirectory() + " and " + paths.modDirectory(), report.reportFile(),
                    gameDirectory.resolve("logs/mfcl/runtime-1.7.10.json").toString());
        }

        /** Mod IDs are case-sensitive; catch the common lower-case ID and name-instead-of-ID mistakes. */
        Found similar(String id) {
            for (var mod : discovered.values())
                if (mod.id().equalsIgnoreCase(id) || mod.name().equalsIgnoreCase(id)) return mod;
            return null;
        }

        /** Shared cause section appended once below every failed requirement. */
        public String explain() {
            var text = new StringBuilder();
            if (!runtimeStarted) {
                text.append("\n\n");
                if (state == LegacyRuntimeService.State.NO_LEGACY_MODS) {
                    text.append("MFCL found no Forge 1.7.10 mod JARs in ").append(scanned).append('.');
                } else {
                    text.append(state == LegacyRuntimeService.State.DEPENDENCY_ERRORS
                            ? "MFCL did not start the 1.7.10 runtime because it found the problems below, so no 1.7.10 mod was loaded."
                            : "The 1.7.10 runtime failed while starting, so no 1.7.10 mod was loaded. The stack trace in "
                              + runtimeReport + " usually names the mod that crashed.");
                    text.append("\nThese problems can come from ANY 1.7.10 mod in the folder (a missing dependency, a duplicate or a conflict),"
                            + " not only the mod named above. Fix or remove the mods they name:");
                    if (blockers.isEmpty()) text.append("\n  (no recorded cause; state ").append(state).append(')');
                    blockers.stream().limit(MAX_BLOCKERS).forEach(blocker ->
                            text.append("\n  [").append(blocker.code()).append("] ").append(blocker.message()));
                    if (blockers.size() > MAX_BLOCKERS)
                        text.append("\n  ... and ").append(blockers.size() - MAX_BLOCKERS).append(" more in ").append(bootReport);
                }
            }
            text.append("\n\nDetails: ").append(bootReport).append(" (JARs found and dependency checks), ")
                    .append(runtimeReport).append(" (1.7.10 boot result and stack trace).");
            return text.toString();
        }
    }

    private record Requirement(String id, String version, VersionRange range, boolean mandatory, String side) {
        static Requirement read(IConfigurable entry) {
            String id = string(entry, "modId", null);
            if (id == null || id.isBlank()) throw new IllegalArgumentException("modId must be a nonempty original mod ID");
            String version = string(entry, "versionRange", "");
            Object required = entry.getConfigElement("mandatory").orElse(true);
            if (!(required instanceof Boolean mandatory)) throw new IllegalArgumentException("mandatory must be a boolean");
            String side = string(entry, "side", "BOTH");
            if (!Set.of("BOTH", "CLIENT", "SERVER").contains(side))
                throw new IllegalArgumentException("side must be BOTH, CLIENT or SERVER");
            if (entry.getConfigElement("ordering").isPresent())
                throw new IllegalArgumentException("ordering is unsupported for isolated legacy mods; order the modern addon after mfcl instead");
            VersionRange range = null;
            try {
                if (version.startsWith("[") || version.startsWith("(")) range = VersionRange.createFromVersionSpec(version);
            } catch (InvalidVersionSpecificationException invalid) {
                throw new IllegalArgumentException("invalid versionRange " + version, invalid);
            }
            return new Requirement(id, version, range, mandatory, side);
        }

        String check(Map<String, LegacyModInfo> loaded, Dist dist, Context context) {
            if (side.equals("CLIENT") && dist != Dist.CLIENT || side.equals("SERVER") && dist != Dist.DEDICATED_SERVER)
                return null;
            LegacyModInfo mod = loaded.get(id);
            String required = "requires original Forge 1.7.10 mod " + id + " " + displayVersion();
            if (mod == null) return mandatory ? required + ", but it was not loaded: " + absence(context) : null;
            if (version.isEmpty() || matches(mod.version())) return null;
            var found = context.discovered().get(id);
            return required + ", but the loaded one is " + (mod.version().isBlank() ? "an unknown version" : "version " + mod.version())
                    + (found == null ? "" : " (" + found.file() + ")") + "; replace it with a matching version.";
        }

        /** Distinguishes JAR not found / wrong ID / wrong version / whole runtime blocked / disabled by legacy FML. */
        private String absence(Context context) {
            var found = context.discovered().get(id);
            if (found == null) {
                var similar = context.similar(id);
                return "no JAR declaring mod ID " + id + " was found in " + context.scanned()
                        + (similar == null ? "." : ". Did you mean " + similar.id() + " (" + similar.file()
                        + ")? Mod IDs are case-sensitive, so the addon or the JAR is the wrong one.");
            }
            String jar = "its JAR " + found.file() + " was found";
            if (!version.isEmpty() && !found.version().isBlank() && !matches(found.version()))
                jar += " but declares version " + found.version() + ", which does not match " + version;
            return context.runtimeStarted()
                    ? jar + " and the 1.7.10 runtime started, but legacy FML did not activate " + id + "; see " + context.runtimeReport() + "."
                    : jar + ", but the 1.7.10 runtime did not start, so no 1.7.10 mod was loaded (cause below).";
        }

        private boolean matches(String actual) {
            if (actual.isBlank()) return false;
            return range == null ? version.equals(actual) : range.containsVersion(new DefaultArtifactVersion(actual));
        }

        private String displayVersion() { return version.isEmpty() ? "(any version)" : version; }
        private static String string(IConfigurable entry, String key, String fallback) {
            Object value = entry.getConfigElement(key).orElse(fallback);
            if (value == null) return null;
            if (!(value instanceof String text)) throw new IllegalArgumentException(key + " must be a string");
            return text;
        }
    }
}

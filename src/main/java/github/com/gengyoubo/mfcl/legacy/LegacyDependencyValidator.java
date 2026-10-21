package github.com.gengyoubo.mfcl.legacy;

import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class LegacyDependencyValidator {
    public List<LegacyDiagnostic> validate(List<LegacyArchive> archives) {
        List<LegacyDiagnostic> diagnostics = new ArrayList<>();
        Map<String, LegacyMod> mods = new HashMap<>();
        Map<String, String> modOwners = new HashMap<>();
        Map<String, String> archiveOwners = new HashMap<>();
        Map<String, String> classOwners = new HashMap<>();
        Set<String> hashes = new HashSet<>();
        for (LegacyArchive archive : archives) {
            String sameArchive = archiveOwners.putIfAbsent(archive.sha256(), archive.file());
            if (!hashes.add(archive.sha256())) diagnostics.add(LegacyDiagnostic.error("DUPLICATE_ARCHIVE", "Same archive supplied more than once: "
                    + sameArchive + " and " + archive.file() + "; keep only one (mods/ or mods/1.7.10/)"));
            for (String name : archive.classes()) {
                String owner = classOwners.putIfAbsent(name, archive.file());
                if (owner != null) diagnostics.add(LegacyDiagnostic.error("DUPLICATE_CLASS", name + " exists in " + owner + " and " + archive.file()));
            }
            for (LegacyMod mod : archive.mods()) {
                String owner = modOwners.putIfAbsent(mod.id(), archive.file());
                if (mods.putIfAbsent(mod.id(), mod) != null) diagnostics.add(LegacyDiagnostic.error("DUPLICATE_MOD_ID", "Duplicate legacy mod ID " + mod.id()
                        + " in " + owner + " and " + archive.file() + "; keep only one version"));
                String accepted = mod.minecraftVersion();
                if (!accepted.isBlank() && !satisfies("1.7.10", accepted, diagnostics)) {
                    diagnostics.add(LegacyDiagnostic.error("WRONG_MINECRAFT_VERSION", mod.id() + " requires Minecraft " + accepted));
                }
            }
        }
        for (LegacyArchive archive : archives) {
            for (String reference : archive.requiredMods()) {
                String[] parts = reference.split("@", 2);
                String id = parts[0];
                if (id.equals("Minecraft") || id.equals("minecraft") || id.equals("Forge") || id.equals("FML")) continue;
                LegacyMod dependency = mods.get(id);
                if (dependency == null) diagnostics.add(LegacyDiagnostic.error("MISSING_MOD_DEPENDENCY", archive.file() + " requires " + reference + "; place the dependency in mods/ or mods/1.7.10/"));
                else if (parts.length == 2 && !satisfies(dependency.version(), parts[1], diagnostics)) {
                    diagnostics.add(LegacyDiagnostic.error("DEPENDENCY_VERSION", archive.file() + " requires " + reference + ", found " + dependency.version()));
                }
            }
            for (String name : archive.requiredClasses()) {
                if (!classOwners.containsKey(name)) diagnostics.add(LegacyDiagnostic.error("MISSING_LIBRARY_DEPENDENCY", archive.file() + " requires class " + name + "; place the legacy library in mods/ or mods/1.7.10/"));
            }
        }
        return List.copyOf(diagnostics);
    }

    private static boolean satisfies(String version, String requirement, List<LegacyDiagnostic> diagnostics) {
        if (version.isBlank()) return false;
        try {
            if (!(requirement.startsWith("[") || requirement.startsWith("("))) return version.equals(requirement);
            return VersionRange.createFromVersionSpec(requirement).containsVersion(new DefaultArtifactVersion(version));
        } catch (InvalidVersionSpecificationException invalid) {
            diagnostics.add(LegacyDiagnostic.error("INVALID_VERSION_REQUIREMENT", requirement + ": " + invalid.getMessage()));
            return false;
        }
    }
}

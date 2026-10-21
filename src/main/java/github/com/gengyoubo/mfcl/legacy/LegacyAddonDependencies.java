package github.com.gengyoubo.mfcl.legacy;

import github.com.gengyoubo.mfcl.api.LegacyModInfo;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.forgespi.language.IConfigurable;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Checks modern addons' mfclDependencies tables against successfully loaded original mods. */
public final class LegacyAddonDependencies {
    private LegacyAddonDependencies() { }

    public static List<String> validateFile(Set<String> owners, IConfigurable config,
                                            Map<String, LegacyModInfo> loaded, Dist dist) {
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
                        String failure = requirement.check(loaded, dist);
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

        String check(Map<String, LegacyModInfo> loaded, Dist dist) {
            if (side.equals("CLIENT") && dist != Dist.CLIENT || side.equals("SERVER") && dist != Dist.DEDICATED_SERVER)
                return null;
            LegacyModInfo mod = loaded.get(id);
            if (mod == null) return mandatory ? "requires original Forge 1.7.10 mod " + id + " " + displayVersion()
                    + ", but it was not successfully loaded. Install it and its legacy dependencies in mods/ or mods/1.7.10/; "
                    + "check logs/mfcl/legacy-1.7.10.json and runtime-1.7.10.json for runtime failures." : null;
            if (version.isEmpty()) return null;
            boolean matches = !mod.version().isBlank() && (range == null ? version.equals(mod.version())
                    : range.containsVersion(new DefaultArtifactVersion(mod.version())));
            return matches ? null : "requires original Forge 1.7.10 mod " + id + " " + displayVersion()
                    + ", found " + (mod.version().isBlank() ? "an unknown version" : mod.version());
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

package github.com.gengyoubo.mfcl.legacy;

import java.util.List;
import java.util.Set;

public record LegacyArchive(String file, String sha256, List<LegacyMod> mods,
                            String corePlugin, String coreContainer, List<String> accessTransformers,
                            Set<String> classes, List<String> requiredMods, List<String> requiredClasses) {
    public LegacyArchive {
        mods = List.copyOf(mods);
        accessTransformers = List.copyOf(accessTransformers);
        classes = java.util.Collections.unmodifiableSet(new java.util.LinkedHashSet<>(classes));
        requiredMods = List.copyOf(requiredMods);
        requiredClasses = List.copyOf(requiredClasses);
    }
}

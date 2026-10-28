package github.com.gengyoubo.mfcl.legacy;

import java.util.List;

public record LegacyMod(String id, String name, String version, String minecraftVersion,
                        String entryClass, String metadataResource, List<String> requiredMods, String logoFile) {
    public LegacyMod { requiredMods = List.copyOf(requiredMods); logoFile = logoFile == null ? "" : logoFile; }
    public LegacyMod(String id, String name, String version, String minecraftVersion,
                     String entryClass, String metadataResource, List<String> requiredMods) {
        this(id, name, version, minecraftVersion, entryClass, metadataResource, requiredMods, "");
    }
}

package github.com.gengyoubo.mfcl.legacy;

import java.util.List;

public record LegacyMod(String id, String name, String version, String minecraftVersion,
                        String entryClass, String metadataResource, List<String> requiredMods) {
    public LegacyMod { requiredMods = List.copyOf(requiredMods); }
}

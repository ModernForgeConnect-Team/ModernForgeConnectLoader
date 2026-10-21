package github.com.gengyoubo.mfcl.legacy;

import java.nio.file.Path;

public record LegacyPaths(Path gameDirectory) {
    public LegacyPaths {
        gameDirectory = gameDirectory.toAbsolutePath().normalize();
    }

    public Path modDirectory() { return rootModDirectory().resolve("1.7.10"); }
    /** The modern mods folder; legacy JARs a launcher installs here are recognized too (see {@link LegacyDiscovery}). */
    public Path rootModDirectory() { return gameDirectory.resolve("mods"); }
    public Path reportFile() { return gameDirectory.resolve("logs").resolve("mfcl").resolve("legacy-1.7.10.json"); }
}

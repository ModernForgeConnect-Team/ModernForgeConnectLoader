package github.com.gengyoubo.mfcl.legacy;

import java.nio.file.Path;

public record LegacyPaths(Path gameDirectory) {
    public LegacyPaths {
        gameDirectory = gameDirectory.toAbsolutePath().normalize();
    }

    public Path modDirectory() { return gameDirectory.resolve("mods").resolve("1.7.10"); }
    public Path reportFile() { return gameDirectory.resolve("logs").resolve("mfcl").resolve("legacy-1.7.10.json"); }
}

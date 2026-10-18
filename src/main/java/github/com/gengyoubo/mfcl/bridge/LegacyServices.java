package github.com.gengyoubo.mfcl.bridge;

import java.nio.file.Path;

/** One object identity domain shared by every optional adapter in a runtime. */
public final class LegacyServices {
    private final Path gameDirectory;
    private final ClassLoader loader;
    private final LegacyClientData items;
    public LegacyServices(Path gameDirectory, ClassLoader loader) throws Exception {
        this.gameDirectory = gameDirectory; this.loader = loader; this.items = new LegacyClientData(loader);
    }
    public Path gameDirectory() { return gameDirectory; }
    public ClassLoader loader() { return loader; }
    public LegacyClientData items() { return items; }
}

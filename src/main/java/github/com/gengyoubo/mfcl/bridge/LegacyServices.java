package github.com.gengyoubo.mfcl.bridge;

import java.nio.file.Path;

/** One object identity domain shared by every optional adapter in a runtime. */
public final class LegacyServices {
    private final Path gameDirectory;
    private final ClassLoader loader;
    private final LegacyClientData items;
    private final LegacyWorlds worlds;
    private final LegacyScreens screens;
    private final LegacyContainers containers;
    public LegacyServices(Path gameDirectory, ClassLoader loader) throws Exception {
        this(gameDirectory, loader, new LegacyClientData(loader));
    }
    public LegacyServices(Path gameDirectory, ClassLoader loader, LegacyClientData items) throws Exception {
        this.gameDirectory = gameDirectory; this.loader = loader; this.items = items;
        this.items.initializeContext(gameDirectory, java.util.List.of("assets/minecraft/lang/en_US.lang"));
        this.items.refreshIconMetadata();
        this.items.loadRegistryCatalog();
        this.worlds = new LegacyWorlds(loader); this.screens = new LegacyScreens(loader);
        this.containers = new LegacyContainers(this);
    }
    public Path gameDirectory() { return gameDirectory; }
    public ClassLoader loader() { return loader; }
    public LegacyClientData items() { return items; }
    public LegacyWorlds worlds() { return worlds; }
    public LegacyScreens screens() { return screens; }
    public LegacyContainers containers() { return containers; }
}

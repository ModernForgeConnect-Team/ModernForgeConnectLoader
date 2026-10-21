package github.com.gengyoubo.mfcl.bridge;

import java.nio.file.Path;

/** One object identity domain shared by every optional adapter in a runtime. */
public final class LegacyServices {
    private final Path gameDirectory;
    private final ClassLoader loader;
    private final LegacyClientData items;
    private final LegacyWorlds worlds;
    private final LegacyBiomes biomes;
    private final LegacyScreens screens;
    private final LegacyContainers containers;
    private final LegacyTiles tiles;
    private final LegacyPlayerData playerData;
    private final LegacyBlockLogic logic;
    private final LegacyInventoryAccess inventories;
    private final LegacyEnergyAccess energy;
    private final LegacyPlayerInventory playerInventory;
    private final LegacyPlayerState playerState = new LegacyPlayerState();
    private final LegacyDynamicItems dynamicItems = new LegacyDynamicItems();
    private final LegacyHeldScanners scanners = new LegacyHeldScanners();
    private final LegacyDimensions dimensions;
    private final LegacyEntityHosting entityHosting;
    public LegacyServices(Path gameDirectory, ClassLoader loader) throws Exception {
        this(gameDirectory, loader, new LegacyClientData(loader));
    }
    public LegacyServices(Path gameDirectory, ClassLoader loader, LegacyClientData items) throws Exception {
        this.gameDirectory = gameDirectory; this.loader = loader; this.items = items;
        this.items.initializeContext(gameDirectory, java.util.List.of("assets/minecraft/lang/en_US.lang"));
        this.items.refreshIconMetadata();
        this.items.loadRegistryCatalog();
        this.biomes = new LegacyBiomes(loader);
        this.worlds = new LegacyWorlds(items,biomes); this.screens = new LegacyScreens(loader);
        this.dimensions = new LegacyDimensions(loader,worlds);
        this.containers = new LegacyContainers(this);
        this.tiles = new LegacyTiles(this);
        this.playerData = new LegacyPlayerData(this);
        this.logic = new LegacyBlockLogic(this); this.inventories = new LegacyInventoryAccess(this);
        this.energy = new LegacyEnergyAccess(this);
        this.playerInventory = new LegacyPlayerInventory(this);
        this.entityHosting = new LegacyEntityHosting(this);
    }
    public Path gameDirectory() { return gameDirectory; }
    public ClassLoader loader() { return loader; }
    public LegacyClientData items() { return items; }
    public LegacyWorlds worlds() { return worlds; }
    public LegacyBiomes biomes() { return biomes; }
    public LegacyDimensions dimensions() { return dimensions; }
    public LegacyScreens screens() { return screens; }
    public LegacyContainers containers() { return containers; }
    public LegacyTiles tiles() { return tiles; }
    public LegacyPlayerData playerData() { return playerData; }
    public LegacyBlockLogic logic() { return logic; }
    public LegacyInventoryAccess inventories() { return inventories; }
    public LegacyEnergyAccess energy() { return energy; }
    public LegacyPlayerInventory playerInventory() { return playerInventory; }
    public LegacyPlayerState playerState() { return playerState; }
    public LegacyDynamicItems dynamicItems() { return dynamicItems; }
    public LegacyHeldScanners scanners() { return scanners; }
    public LegacyEntityHosting entityHosting() { return entityHosting; }
    private final java.util.Map<String, java.util.Set<String>> hostItemTags = new java.util.LinkedHashMap<>();
    /** Puts an original item into a host item tag (e.g. minecraft:arrows), for host behavior the original game never had. */
    public synchronized void tagItem(String hostTag, String legacyKey) { hostItemTags.computeIfAbsent(hostTag, ignored -> new java.util.LinkedHashSet<>()).add(legacyKey); }
    public synchronized java.util.Map<String, java.util.Set<String>> hostItemTags() { return java.util.Map.copyOf(hostItemTags); }
    /** UUID of an old Entity (player actors included); null for other old objects such as command senders. */
    public java.util.UUID entityId(Object old) {
        try {
            var type = loader.loadClass("net.minecraft.entity.Entity");
            return type.isInstance(old) ? (java.util.UUID) type.getMethod("func_110124_au").invoke(old) : null;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
}

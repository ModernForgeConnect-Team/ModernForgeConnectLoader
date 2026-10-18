package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.Field;

/** Temporary, read-only scene for old inventory renderers that read a player, viewer, or world RNG. */
public final class LegacyInventoryContext implements AutoCloseable {
    private final LegacyServices services;
    private final Object identity = new Object();
    private final Object client, player, playerWorld, viewer, tileDispatcher, tileViewer;
    private final Field worldField, viewerField, tileViewerField;
    private final Object entityRenderer, entityWorld, entityViewer;
    private final Field entityWorldField, entityViewerField;
    public LegacyInventoryContext(LegacyServices services) throws Exception {
        this.services = services;
        services.containers().preparePlayer();
        ClassLoader loader = services.loader();
        Class<?> mc = loader.loadClass("net.minecraft.client.Minecraft");
        client = mc.getMethod("func_71410_x").invoke(null);
        player = mc.getField("field_71439_g").get(client);
        worldField = loader.loadClass("net.minecraft.entity.Entity").getField("field_70170_p"); playerWorld = worldField.get(player);
        viewerField = mc.getField("field_71451_h"); viewer = viewerField.get(client);
        Class<?> dispatcher = loader.loadClass("net.minecraft.client.renderer.tileentity.TileEntityRendererDispatcher");
        tileDispatcher = dispatcher.getField("field_147556_a").get(null);
        tileViewerField = dispatcher.getField("field_147551_g"); tileViewer = tileViewerField.get(tileDispatcher);
        Class<?> manager = loader.loadClass("net.minecraft.client.renderer.entity.RenderManager");
        entityRenderer = manager.getField("field_78727_a").get(null);
        entityWorldField = manager.getField("field_78722_g"); entityWorld = entityWorldField.get(entityRenderer);
        entityViewerField = manager.getField("field_78734_h"); entityViewer = entityViewerField.get(entityRenderer);
        Object world = services.worlds().bind(identity, new HostWorldAccess() {
            public String dimensionKey() { return "mfcl:inventory_preview"; }
            public int legacyDimensionId() { return 0; }
            public boolean clientSide() { return true; }
            public long gameTime() { return 0; }
            public long dayTime() { return 0; }
            public Block block(int x, int y, int z) { return new Block("minecraft:air", 0); }
            public Object tileEntity(int x, int y, int z) { return null; }
            public void setDayTime(long time) { throw new UnsupportedOperationException("Inventory preview is read-only"); }
            public boolean setBlock(int x, int y, int z, Block block, int flags) { throw new UnsupportedOperationException("Inventory preview is read-only"); }
        });
        worldField.set(player, world); viewerField.set(client, player); tileViewerField.set(tileDispatcher, player);
        entityWorldField.set(entityRenderer, world); entityViewerField.set(entityRenderer, player);
    }
    @Override public void close() throws Exception {
        try {
            worldField.set(player, playerWorld); viewerField.set(client, viewer); tileViewerField.set(tileDispatcher, tileViewer);
            entityWorldField.set(entityRenderer, entityWorld); entityViewerField.set(entityRenderer, entityViewer);
        }
        finally { services.worlds().unbind(identity); }
    }
}

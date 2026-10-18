package github.com.gengyoubo.mfcl.bridge;

import java.lang.reflect.*;
import java.util.*;

/** Executes the original multipart collision and selection methods against a read-only scene. */
public final class LegacyBlockShapes {
    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {}
    public record Shapes(List<Box> collision, List<Box> outline) {
        public Shapes { collision = List.copyOf(collision); outline = List.copyOf(outline); }
    }
    private final LegacyServices services;
    private final Class<?> blockType, worldType, accessType, boxType;
    private final Object registry;
    private final Method getBlock, stateBounds, addCollision, selection, makeBox;
    private record Cell(HostWorldAccess.Block block, java.nio.ByteBuffer tile) {}
    private final Map<List<Cell>, Shapes> cache = new LinkedHashMap<>(128,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<List<Cell>, Shapes> entry) { return size() > 512; }
    };
    public LegacyBlockShapes(LegacyServices services) throws Exception {
        this.services = services;
        var loader = services.loader();
        blockType = loader.loadClass("net.minecraft.block.Block"); worldType = loader.loadClass("net.minecraft.world.World");
        accessType = loader.loadClass("net.minecraft.world.IBlockAccess"); boxType = loader.loadClass("net.minecraft.util.AxisAlignedBB");
        registry = blockType.getField("field_149771_c").get(null);
        getBlock = registry.getClass().getMethod("func_82594_a", String.class);
        stateBounds = blockType.getMethod("func_149719_a", accessType, int.class, int.class, int.class);
        addCollision = blockType.getMethod("func_149743_a", worldType, int.class, int.class, int.class, boxType, List.class, loader.loadClass("net.minecraft.entity.Entity"));
        selection = blockType.getMethod("func_149633_g", worldType, int.class, int.class, int.class);
        makeBox = boxType.getMethod("func_72330_a", double.class, double.class, double.class, double.class, double.class, double.class);
    }
    public Shapes shapes(String key, LegacyBlockRendering.View view) throws Exception {
        return services.items().inLegacy(() -> {
            var cells = new ArrayList<Cell>(27);
            for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
                byte[] tile = view.tileData(x,y,z);
                cells.add(new Cell(view.block(x,y,z), java.nio.ByteBuffer.wrap(tile == null ? new byte[0] : tile).asReadOnlyBuffer()));
            }
            Shapes known = cache.get(cells); if (known != null) return known;
            Object block = getBlock.invoke(registry, key);
            try (var scene = new LegacyBlockScene(services, view)) {
                stateBounds.invoke(block, scene.world(), 0,0,0);
                var old = new ArrayList<Object>();
                Object query = makeBox.invoke(null, -8d,-8d,-8d,8d,8d,8d);
                addCollision.invoke(block, scene.world(), 0,0,0,query,old,null);
                var collisions = new ArrayList<Box>(); for (Object value : old) collisions.add(box(value));
                stateBounds.invoke(block, scene.world(), 0,0,0);
                Object selected = selection.invoke(block, scene.world(), 0,0,0);
                var result = new Shapes(collisions, selected == null ? List.of() : List.of(box(selected)));
                cache.put(List.copyOf(cells), result); return result;
            } finally { blockType.getMethod("func_149683_g").invoke(block); }
        });
    }
    private Box box(Object old) throws Exception {
        return new Box(boxType.getField("field_72340_a").getDouble(old), boxType.getField("field_72338_b").getDouble(old), boxType.getField("field_72339_c").getDouble(old),
                boxType.getField("field_72336_d").getDouble(old), boxType.getField("field_72337_e").getDouble(old), boxType.getField("field_72334_f").getDouble(old));
    }
    public int placement(String key, Object world, int x, int y, int z, int side, float hitX, float hitY, float hitZ, int metadata) throws Exception {
        return services.items().inLegacy(() -> (int)blockType.getMethod("func_149660_a", worldType, int.class,int.class,int.class,int.class,float.class,float.class,float.class,int.class)
                .invoke(getBlock.invoke(registry,key),world,x,y,z,side,hitX,hitY,hitZ,metadata));
    }
}

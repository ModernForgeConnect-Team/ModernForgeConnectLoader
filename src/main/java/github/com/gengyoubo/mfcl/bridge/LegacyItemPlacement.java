package github.com.gengyoubo.mfcl.bridge;

/** Original ItemBlock and special Item placement callbacks, including their NBT and stack consumption. */
public final class LegacyItemPlacement {
    public record Result(boolean used, LegacyClientData.Stack stack) {}
    private final LegacyServices services;
    public LegacyItemPlacement(LegacyServices services) { this.services = services; }
    public Result use(Object world, LegacyTiles.Placer placer, LegacyClientData.Stack snapshot,
                      int x, int y, int z, int side, float hitX, float hitY, float hitZ) throws Exception {
        return services.items().inLegacy(() -> {
            var loader = services.loader();
            Class<?> itemType = loader.loadClass("net.minecraft.item.Item"), stackType = loader.loadClass("net.minecraft.item.ItemStack");
            Class<?> playerType = loader.loadClass("net.minecraft.entity.player.EntityPlayer"), worldType = loader.loadClass("net.minecraft.world.World");
            Object stack = services.items().materialize(snapshot), item = stackType.getMethod("func_77973_b").invoke(stack);
            Object player = services.tiles().actor(world, placer, snapshot);
            Class<?>[] signature = {stackType,playerType,worldType,int.class,int.class,int.class,int.class,float.class,float.class,float.class};
            Object[] arguments = {stack,player,world,x,y,z,side,hitX,hitY,hitZ};
            boolean used = (boolean)itemType.getMethod("onItemUseFirst",signature).invoke(item,arguments);
            if (!used) used = (boolean)itemType.getMethod("func_77648_a",signature).invoke(item,arguments);
            int count = stackType.getField("field_77994_a").getInt(stack);
            return new Result(used, count <= 0 ? null : services.items().snapshot(stack));
        });
    }
}
